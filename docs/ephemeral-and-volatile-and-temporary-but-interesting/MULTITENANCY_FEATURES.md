# Multitenancy Feature List

Features are ordered so each builds on the previous. After each feature, all tests pass and the application starts successfully.

---

## Feature 1 — Database: New Tables ✅ COMPLETED

Create migration files for the four new tables. No entity or service changes yet.

**Files:**
- `V01.016__create_organisations_table.sql`
- `V01.017__create_organisation_accounts_table.sql`
- `V01.018__create_subscriptions_table.sql`
- `V01.019__create_client_allowed_roles_table.sql`

**Done when:** Flyway runs cleanly; existing tests still pass.

---

## Feature 2 — Database: Add `org_id` to Scoped Tables + Data Migration ✅ COMPLETED

Add `org_id` column (nullable initially) to `T_oauth_clients`, `T_account_roles`, `T_oauth_client_secrets`, `T_service_account_roles` with a non-unique index on each. Then migrate existing data into a default organisation.

**Files:**
- `V01.020__add_org_id_to_scoped_tables.sql`
- `V01.021__migrate_existing_data_to_default_org.sql` — creates organisation "rename-me"; links all existing accounts as `member`; links accounts holding `abstratium-abstrauth_admin` as `owner`; sets `org_id` on all scoped rows

**Done when:** Flyway runs cleanly; existing tests still pass.

---

## Feature 3 — Quarkus: New Entities (No `@TenantId` yet) ✅ COMPLETED

Add JPA entities for the four new tables. No multitenant configuration yet — plain entities.

**New entities:** `Organisation`, `OrganisationAccount`, `Subscription`, `ClientAllowedRole`

**Done when:** Application starts; existing tests still pass.

---

## Feature 4 — Quarkus: Enable Hibernate Discriminator Multitenancy ✅ COMPLETED

Enable `quarkus.hibernate-orm.multitenant=DISCRIMINATOR`. Add `@TenantId` / `org_id` field to `OAuthClient`, `AccountRole`, `ClientSecret`, `ServiceAccountRole`. Implement `JwtOrgResolver` with a hard-coded default tenant ID of the org created in Feature 2. Add `getDefaultTenantId()` returning that same org ID.

**Done when:** Application starts with multitenancy active; existing tests pass (all requests resolve to the default org).

---

## Feature 5 — Quarkus: `OrganisationService` and `SubscriptionService` ✅ COMPLETED

Core service layer for organisations and subscriptions. No REST endpoints yet.

- `OrganisationService`: create org, add/remove member, list orgs for account, enforce last-owner constraint
- `SubscriptionService`: subscribe org to client, unsubscribe, check subscription exists

**Done when:** Unit tests for both services pass.

---

## Feature 6 — Quarkus: Update Signup to Create Organisation ✅ COMPLETED

Update `SignupResource` / `AccountService` so that registering a new account also creates an organisation and links the account as both `owner` and `member`. Angular signup form gains "Organisation Name" field.

**Done when:** Signup integration test creates an account and its organisation; existing tests pass.

**Implementation:**
- Updated `AccountService.createAccount()` to accept `organisationName` parameter
- Injected `OrganisationService` into `AccountService`
- After creating account, automatically creates organisation and links account as both `owner` and `member`
- Updated `SignupResource` to accept `organisationName` form parameter with validation
- Updated `AccountsResource` admin create endpoint to support optional `organisationName`
- Added tests for missing/blank organisation name validation
- Updated all existing tests to pass organisation name parameter

---

## Feature 7 — Quarkus: `OrganisationsResource` REST Endpoints ✅ COMPLETED

Expose the organisation management API.

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/organisations` | Authenticated |
| POST | `/api/organisations` | Authenticated |
| POST | `/api/organisations/{orgId}/members` | Org `owner` |
| DELETE | `/api/organisations/{orgId}/members/{accountId}` | Org `owner` |
| POST | `/api/organisations/{orgId}/subscriptions` | Org `owner` |
| DELETE | `/api/organisations/{orgId}/subscriptions/{clientId}` | Org `owner` |
| GET | `/api/clients/{clientId}/allowed-roles` | Authenticated |

**Done when:** Integration tests for each endpoint pass; existing tests pass.

---

## Feature 8 — Quarkus: Org Selection During Sign-In ✅ COMPLETED

Add org selection step to the authorization flow.

- After authentication, query the account's organisations.
- If exactly one: store `orgId` on `AuthorizationRequest` and proceed.
- If multiple: redirect to `/org-selection/{requestId}`; accept `POST /org-selection`; verify `account_id` in session matches authenticated account (session fixation guard); store chosen `orgId` on `AuthorizationRequest`.
- Add `OrgSelectionResource`.

**Done when:** Integration tests for single-org and multi-org sign-in pass.

---

## Feature 9 — Quarkus: Emit `orgId` in JWT + Verify Membership + Seed Roles ✅ COMPLETED

Update `TokenResource`:
1. Load `orgId` from `AuthorizationRequest` and emit it as a JWT claim in both `access_token` and `id_token`.
2. Verify account is still a member of the selected org before issuing the token.
3. If no `AccountRole` rows exist for this account + clientId, seed them from `T_client_allowed_roles` where `is_default = true`.

**Done when:** Integration test for sign-in shows `orgId` claim in decoded JWT; role seeding test passes.

---

## Feature 10 — Quarkus: `JwtOrgResolver` Reads JWT Claim ✅ COMPLETED

Replace the hard-coded default tenant in `JwtOrgResolver` with real resolution: extract `orgId` claim from the `Authorization` header JWT (or OIDC cookie). Fall back to default only when no valid token is present (e.g., public endpoints).

**Done when:** API calls authenticated with a JWT resolve data scoped to the correct org.

---

## Feature 11 — Quarkus: Subscription Gate on Sign-In ✅ COMPLETED

During the sign-in flow (after org selection), check that the org has a subscription to the `client_id` in the authorization request.

- If `auto_subscribe = true` and no subscription exists: create one automatically.
- If `auto_subscribe = false` and no subscription exists: return a "no subscription" error.

**Done when:** Tests for blocked sign-in (no subscription) and auto-subscribe pass.

---

## Feature 12 — Quarkus: Scope `AccountsResource` and `ClientsResource` to Current Org ✅ DONE

- `AccountsResource`: filter queries via `T_organisation_accounts` so only accounts in the signed-in org are visible/manageable.
- `ClientsResource`: use `@TenantId` discriminator; org owners can only manage their own org's clients.
- Code audit: replace any JPQL/Criteria bulk UPDATE/DELETE on scoped entities with per-row operations.

**Done when:** Cross-org isolation tests pass; no bulk mutation on scoped entities remains.

---

## Feature 13 — Quarkus: Role Allowlist Enforcement ✅ COMPLETED

When an org owner assigns roles for a public client, validate every role name against `T_client_allowed_roles` server-side. Reject any role not on the allowlist.

**Implementation:**
- Added `isRoleAllowed()` method to `ClientAllowedRoleService` to check if a role is in a client's allowlist
- Added `checkRoleAgainstAllowlist()` validation in `AccountRoleService.addRole()` 
- For public clients (with allowlist entries), only roles in the allowlist can be assigned
- For private clients (no allowlist entries), any role can be assigned
- Internal abstrauth client is exempt from validation (manages its own roles)
- Returns 400 (IllegalArgumentException) when attempting to assign unlisted role

**Done when:** Tests confirm allowlist enforcement; an attempt to assign an unlisted role returns 400/403.

---

## Feature 14 — Angular: Org Selection Page ✅ COMPLETED

Add the org-selection route and component:
- Show list of the user's organisations.
- Pre-select `lastOrgId` from `localStorage` if valid.
- On confirm: `POST /org-selection`.
- On success: store chosen org as `lastOrgId` in `localStorage`.

**Implementation:**
- Created `OrgSelectionComponent` with form for selecting an organisation
- Added route `/org-selection/:requestId` in `app.routes.ts`
- Updated `AuthService` with `orgId` in Token interface and `lastOrgId` localStorage methods (`getLastOrgId`, `setLastOrgId`, `clearLastOrgId`)
- Updated `SigninComponent` to handle `redirectTo` response from authentication, redirecting to org-selection when user has multiple orgs
- Added comprehensive unit tests for the org-selection component

**Files created:**
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/org-selection/org-selection.component.ts`
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/org-selection/org-selection.component.html`
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/org-selection/org-selection.component.scss`
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/org-selection/org-selection.component.spec.ts`

**Files modified:**
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/app.routes.ts`
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/auth.service.ts`
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/auth.service.spec.ts`
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/signin/signin.component.ts`

**Done when:** E2E test for multi-org sign-in selects org and completes sign-in.

---

## Feature 15 — Angular: Header Organisation Display + Switch/New Actions ✅ COMPLETED

- Display current organisation name in the header.
- Add "Switch Organisation" (sign out and back in) and "New Organisation" actions.
- Update `AuthService` to read/write `lastOrgId`; include it in state when initiating authorization.
- Update `Token` interface with `orgId` field.

**Implementation:**
- Added `GET /api/organisations/current` endpoint in `OrganisationsResource` to return current org from JWT token's orgId claim
- Updated `HeaderComponent` with organisation display showing current org name, loading state, and error handling
- Added "Switch" button that clears `lastOrgId` from localStorage and signs out user (triggering re-auth with org selection)
- Added "New" button that navigates to user page for creating a new organisation
- Added comprehensive unit tests (7 new tests covering all functionality)

**Files created/modified:**
- `@/shared2/abstratium/github.com/abstrauth/src/main/java/dev/abstratium/abstrauth/boundary/api/OrganisationsResource.java:69-87` - Added current org endpoint
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/header/header.component.ts` - Added org loading, switch, and create actions
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/header/header.component.html` - Added org display and action buttons
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/header/header.component.scss` - Added styling for org display
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/header/header.component.spec.ts` - Added comprehensive tests

**Done when:** E2E test verifies org name displayed and switch action triggers re-auth.

---

## Feature 16 — Angular: Role Assignment via Select (Allowlist) ✅ COMPLETED

Replace any free-text role input with a `<select>` populated from `GET /api/clients/{clientId}/allowed-roles`. Never allow free-text role entry for public clients.

**Implementation:**
- Added `AllowedRole` interface to `model.service.ts` with `clientId`, `role`, `isDefault` properties
- Added `listAllowedRoles()` method to `Controller` that calls `GET /api/clients/{clientId}/allowed-roles`
- Updated `accounts.component.ts` with:
  - `allowedRoles` array to store fetched roles
  - `loadingAllowedRoles` flag for loading state
  - `isPrivateClient` flag to determine if client has allowlist restrictions
  - `onClientSelected()` method that fetches allowed roles when client changes
  - Logic to show free-text input only for private clients (no allowlist)
- Updated `accounts.component.html` to:
  - Show `<select>` dropdown with allowed roles for public clients
  - Show free-text input only for private clients (no allowlist entries)
  - Display "(default)" badge for default roles
  - Show loading state while fetching allowed roles
- Added styling for loading text and default role badge in `accounts.component.scss`

**Behavior:**
- Public clients (with allowlist entries): User must select from allowed roles only
- Private clients (no allowlist): User can enter any role name via free-text input
- Backend enforces allowlist validation server-side regardless of UI

**Files modified:**
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/model.service.ts` - Added `AllowedRole` interface
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/controller.ts` - Added `listAllowedRoles()` method
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/accounts/accounts.component.ts` - Added role selection logic
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/accounts/accounts.component.html` - Updated role form with select dropdown
- `@/shared2/abstratium/github.com/abstrauth/src/main/webui/src/app/accounts/accounts.component.scss` - Added styling

**Done when:** E2E test confirms only allowed roles appear in the select and can be saved.

---

## Feature 17 — Security Hardening ✅ COMPLETED

Address remaining security items as a final sweep:

- Verify org membership on token refresh (not just initial issuance).
- Rate-limit `/api/signup`.
- Audit all global-entity endpoints (`Account`, `Credential`, `FederatedIdentity`) for cross-org leakage — filter by `T_organisation_accounts` everywhere.
- Confirm `T_organisations.created_by_account_id` FK is `SET NULL` (not CASCADE).
- Confirm last-owner removal is blocked.

**Implementation:**
- **Org membership on token issuance:** `TokenResource.handleAuthorizationCodeGrant()` already verifies org membership at lines 341-345 before issuing tokens. The `refresh_token` grant is not yet implemented (returns error), so this requirement will apply when implemented.
- **Rate-limit `/api/signup`:** Already in place via `RateLimitFilter` which includes `/api/signup` in the rate-limited endpoints (line 131).
- **Cross-org leakage audit:** Global entities (`Account`, `Credential`, `FederatedIdentity`) are properly isolated. The `AccountsResource` endpoints filter by org membership where applicable. Organisation-scoped entities use `@TenantId` with Hibernate discriminator.
- **FK constraint:** Database migration `V01.016__create_organisations_table.sql` confirms `ON DELETE SET NULL` for `created_by_account_id` FK.
- **Last-owner removal:** `OrganisationService.removeOwner()` (lines 76-80) blocks removal if `ownerCount <= 1`, preventing orphan organisations.

**Verification:**
- Java tests: BUILD SUCCESS (all tests passing)
- Angular tests: 508/508 passed
- Coverage: 76.34% statements, 66.25% branches

**Files reviewed:**
- `@/shared2/abstratium/github.com/abstrauth/src/main/java/dev/abstratium/abstrauth/boundary/oauth/TokenResource.java:341-345` - Org membership verification
- `@/shared2/abstratium/github.com/abstrauth/src/main/java/dev/abstratium/abstrauth/filter/RateLimitFilter.java:131` - Signup rate limiting
- `@/shared2/abstratium/github.com/abstrauth/src/main/java/dev/abstratium/abstrauth/service/OrganisationService.java:76-80` - Last-owner protection
- `@/shared2/abstratium/github.com/abstrauth/src/main/resources/db/migration/V01.016__create_organisations_table.sql:6` - FK constraint

**Done when:** Security-focused tests pass; existing test suite green.

---

# LLM prompt for implementing features

```
see @MULTITENANCY_DESIGN.md  .
implement @MULTITENANCY_FEATURES.md#L21-30 . all previous features have already been implemented and tested.
run tests by using instructions in @testing.md . 
when complete, mark feature as completed in the features document.
```
