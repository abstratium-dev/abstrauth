# GDPR / Swiss FADP Design

This document records the data-protection design decisions for Abstratium Abstrauth, focusing on the rights to access and erasure under the EU GDPR and the Swiss FADP.

## Scope

The system stores the following categories of personal data:

- Account identity: email, name, profile picture, auth provider.
- Credentials: native username/password hash, lock status, failed login attempts.
- Roles: per-client, per-organisation role assignments.
- Federated identities: external provider user ids, emails, connection timestamps.
- Organisation membership: roles such as member/owner in `T_organisation_accounts`.
- Audit history: Envers revision rows in `*_AUD` tables, including deletion records.

OAuth clients and client secrets are owned by the organisation, not by an individual user. They are not deleted when a single user deletes their account.

## Decisions

1. **Account deletion is a cross-tenant operation.** A user's account, roles, credentials, federated identities and organisation memberships span all organisations they belong to. Deletion is performed through `NonMultitenancyAccountService.deleteAccountWithCascade()` so that no tenant-owned data is left behind.

2. **Organisation memberships are deleted with the account.** `T_organisation_accounts` rows are now removed via JPA `CascadeType.REMOVE` on both the `Account` and `NonMultitenancyAccount` entities.

3. **Transient OAuth data is handled by database cascades.** `T_authorisation_codes` and `T_revoked_tokens` retain `ON DELETE CASCADE` foreign keys and are removed automatically when the account is deleted. This is acceptable because these tables are explicitly excluded from Envers auditing.

4. **Ownership references are removed before implementing deletion.** The columns `T_oauth_client_secrets.account_id` and `T_organisations.created_by_account_id` have been dropped. Client secrets and organisations belong to the organisation, not to the user, so they are not deleted on account deletion.

5. **Single-member organisations are deleted.** If deleting an account leaves an organisation with no remaining members, the organisation is also deleted. This is necessary because the organisation name and metadata are not personal data in a multi-member context, but for a single-person organisation they effectively identify the user.

6. **Audit data is retained for a fixed period, then deleted.** We do not anonymise Envers rows. Instead, we keep the full audit history for a configurable number of days (default proposal: 90 days) and then delete it permanently. The legal basis for this retention is a documented **legitimate interest assessment** (LIA) covering security, abuse investigation and operational accountability.

   **DPO and LIA approval:** DPO stands for **Data Protection Officer**. The LIA must be reviewed and approved by the DPO, or by an external legal advisor if the organisation has not appointed a DPO. The approver must confirm that the need to retain audit history for the chosen period outweighs the user's right to erasure, that the retention period is no longer than necessary, and that no less intrusive alternative is available. The approved LIA must be stored with the project records and reviewed periodically. The audit purge feature must not be released to production until the LIA is approved.

## Deletion Policy

### When a user deletes their own account

The following data is deleted:

- The `T_accounts` row.
- All `T_account_roles` rows for the account.
- All `T_credentials` rows for the account.
- All `T_federated_identities` rows for the account.
- All `T_organisation_accounts` rows for the account.
- All `T_authorisation_requests` rows for the account (manual deletion, no FK).
- All `T_authorisation_codes` rows for the account (DB cascade).
- All `T_revoked_tokens` rows linked to those authorization codes (DB cascade).
- Any organisation where the account was the sole member.

The following data is **not** deleted:

- OAuth clients and their secrets (owned by the organisation).
- Organisations that still have other members.
- Subscriptions of remaining organisations.
- Audit history rows in `*_AUD` tables (handled separately by the retention job).

### Last-admin safeguard

The account deletion endpoint keeps the existing safeguard: an account that has the only `ADMIN` role for the `abstratium-abstrauth` client cannot be deleted. This protects the system from becoming unmanageable.

### Audit retention

All `*_AUD` rows older than the configured retention period are deleted by a scheduled job. The retention period must be:

- Configurable via `application.properties` (e.g. `abstrauth.audit.retention.days`).
- Applied uniformly across all Envers audit tables and `REVINFO` orphans.
- Logged when the purge runs.

## Right of Access (view my data)

Before deleting their account, a user must be able to query all personal data the system holds about them. This is the GDPR/FADP **right of access**.

### Data to include

The response should contain everything that identifies the user, including:

- Account profile: email, name, profile picture, email verified status, auth provider, created at timestamp.
- Credentials: username, password hash, failed login attempts, locked until timestamp.
- Roles: every `T_account_roles` row for the account, including client and organisation context.
- Federated identities: provider, provider user id, email, connected at timestamp.
- Organisation memberships: every `T_organisation_accounts` row, including the org id and role.
- Pending and historical OAuth data: active `T_authorisation_requests` and `T_authorisation_codes` rows for the account (optional, since these are transient and expire quickly).
- Audit history: Envers revision rows from `T_accounts_AUD`, `T_credentials_AUD`, `T_account_roles_AUD`, `T_federated_identities_AUD` and `T_organisation_accounts_AUD` for the account (within the current retention period).

### How the data should be fetched

Because the account, roles, and memberships may span multiple organisations, the query must be **cross-tenant**. The backend should use the non-multitenancy entities (`NonMultitenancyAccount`, `NonMultitenancyCredential`, `NonMultitenancyAccountRole`, `NonMultitenancyFederatedIdentity`, `NonMultitenancyOrganisationAccount`) or a dedicated non-multitenancy service to collect all rows for the authenticated account, regardless of the tenant context derived from the current JWT.

Suggested endpoints:

- `GET /api/accounts/me/data` — returns a structured JSON object grouped by data category.
- `GET /api/accounts/me/data/export` — returns the same data as a downloadable JSON file, with a `Content-Disposition: attachment` header, for portability.

The UI should provide a **"View my data"** page that calls `GET /api/accounts/me/data` and renders the categories clearly, and a **"Download my data"** button that calls the export endpoint.

## User Flow

```mermaid
flowchart TD
    A[User requests account deletion] --> B{Authenticated and not sole abstrauth admin?}
    B -->|No| C[Reject with 400]
    B -->|Yes| D[Confirm by typing email/name]
    D --> E[Call DELETE /api/accounts/me]
    E --> F[Delete account and cascaded entities]
    F --> G{Any org with only this member?}
    G -->|Yes| H[Delete organisation and its subscriptions]
    G -->|No| I[End]
    H --> I
    I --> J[Sign user out]
```

## Implementation Tasks

The work is split into independent features that can be implemented, reviewed, and tested one at a time.

### Feature 1: Self-service account deletion

- [x] Add backend endpoint `DELETE /api/accounts/me` for self-service account deletion.
- [x] Extend `NonMultitenancyAccountService.deleteAccountWithCascade()` to delete single-member organisations.
- [x] Add backend and integration tests for the full deletion cascade.
- [x] Add tests for the single-member organisation deletion path.
- [x] Add UI dialog for self-deletion with confirmation.
- [x] Sign the user out of the UI after successful self-deletion.

### Feature 2: Harder deletion confirmation for accounts and roles

- [ ] Enforce a confirmation step for self-deletion (require the user to type their email or name).
- [ ] Add backend test for the confirmation step (reject deletion if confirmation text does not match).
- [ ] Make deleting accounts and roles harder: require the user to enter the account/role name to confirm the deletion (applies to both self-deletion and administrator deletion of roles).
- [ ] Update the administrator account deletion UI to require the account name/email confirmation.
- [ ] Update the role deletion UI to require the role name confirmation.
- [ ] Add backend tests for administrator account deletion confirmation.
- [ ] Add backend tests for role deletion confirmation.

### Feature 3: Right of access (view my data)

- [ ] Add backend endpoint to view all personal data of the authenticated user (`GET /api/accounts/me/data`).
- [ ] Add backend endpoint to download/export personal data in a machine-readable format (`GET /api/accounts/me/data/export`).
- [ ] Add backend tests for the right of access endpoint and the export endpoint.
- [ ] Add UI page for "view my data".
- [ ] Add "Download my data" button to the view-my-data page.
- [ ] Update the Angular controller to call the new backend endpoints (data view, export, self-delete).

### Feature 4: Audit retention and purge

- [ ] Add configuration property `abstrauth.audit.retention.days` with a sensible default (e.g. 90 days).
- [ ] Periodically remove all audit data that is older than X days, where X is configurable via an environment variable or `application.properties` entry.
- [ ] Add periodic scheduler to delete `*_AUD` rows older than the configured retention period.
- [ ] Decide and document the audit purge schedule (e.g. daily at 02:00 UTC).
- [ ] Log each purge run, including the number of rows deleted and the retention period used.
- [ ] Clean up orphaned `REVINFO` rows that no longer reference any audit table.
- [ ] Add tests for the audit retention purge.

### Feature 5: Documentation and legal approval

- [ ] Document the legitimate interest assessment (LIA) for audit retention and obtain DPO/legal approval.
- [ ] Update the user-facing legal/privacy page to display the currently configured retention period and the user's rights.
- [ ] Update user-facing privacy documentation to state the retention periods and deletion rights.

## Questions and Answers

- Exact retention period for audit data -> default: 90 days, configurable per installation via environment variable.
- Whether a user can request earlier deletion of their audit history, overriding the legitimate interest retention. -> no.
- Whether the right of access should include audit history for the requesting user only. -> no not automatically, but could be requested manually.
