# Admin Role — Current Behaviour and Limitations

## What the `ADMIN` role actually does today

The `ADMIN` role (`abstratium-abstrauth_admin`) is **not** a gate on any HTTP endpoint. No `@RolesAllowed` annotation uses it. Its effect is entirely runtime logic:

| Code location | Effect |
|---|---|
| `AccountsResource.listAccounts()` | Admin sees all accounts in **their current org** (bypasses the client-role filter that `MANAGE_ACCOUNTS` uses) |
| `AccountRoleService.checkNonAdminCannotAddAdminRole()` | Only an admin can assign the `admin` role to another account |
| `AccountService` (bootstrap) | The first registered account automatically receives `admin`, `manage-accounts`, and `manage-clients` |
| `AccountService` / `AccountRoleService` (guards) | The last `admin` row for `abstratium-abstrauth` cannot be deleted |

## The problem

An admin is effectively an org-scoped power user. They see more accounts than a `MANAGE_ACCOUNTS` user (all in the org vs. only those sharing clients), and they can assign roles more freely. But they are **entirely confined to their own org** — they cannot see accounts or clients in other orgs, and there is no cross-org endpoint at all.

This is fine for an **org owner/administrator** role. It is not sufficient for a **platform administrator** who needs to manage the entire abstrauth installation — e.g. to inspect or fix data across all organisations.

## Desired future state

A true platform admin should be able to:

- List accounts across **all** organisations (not filtered by `orgId`)
- List clients across **all** organisations
- Assign roles in any org
- This likely requires a separate role (e.g. `platform-admin`) or a flag on the account, so that it cannot be self-assigned by subscribing orgs (see `Roles.java` comment: org owners must not be able to assign `admin` to their users via a subscription)

Until then, the `ADMIN` role is adequate for day-to-day org administration. The cross-org capability is a future concern.
