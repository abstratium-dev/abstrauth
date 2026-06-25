The entity classes and REST endpoints in this package are ONLY TO BE USED when specifically instructed, because they DO NOT conform to the design principles in the document docs/MULTITENANCY_DESIGN.md.

## Entity Classes (entity/)

These entity classes do not use the hibernate `@TenantId` annotation.

They can be used in the following circumstances:

- The algorithm needs to create data within an organisation where the JwtOrgResolver class cannot determine the orgId to use because the orgId is not yet known at the time when Hibernate adds it to the session, e.g. when creating an account and assigning initial roles.
- Read access for public OAuthClients that a users org is subscribed to. Read access to the same in order to read the allowed roles list.

## REST Endpoints (boundary/)

The `boundary` sub-package contains REST endpoints that perform cross-tenant (cross-organisation) data access. These endpoints are intentionally isolated here to make "dangerous" endpoints that bypass tenant isolation easily identifiable during security audits.

**Location principle**: Any REST endpoint that uses non-multitenancy entities to bypass the Hibernate discriminator should be placed in this package.

### Current Cross-Tenant Endpoints

| Resource | Purpose |
|----------|---------|
| `NonMultitenancyClientsResource` | Access public OAuth clients owned by other organisations that the caller's org subscribes to. **DELETE endpoint**: Deletes clients with cross-tenant cascade (all roles, secrets, subscriptions across all orgs) |
| `NonMultitenancyAccountsResource` | **DELETE endpoint only**: Deletes accounts with cross-tenant cascade (all roles, credentials, federated identities across all orgs) |
| `NonMultitenancyOrganisationsResource` | Create new organisations with initial role assignment (bypasses tenant discriminator before orgId is established in Hibernate session). **DELETE endpoint**: Deletes organisations with cross-tenant cascade (account_roles, organisation_accounts, subscriptions across all orgs) |
| `TokenResource` | OAuth 2.0 token endpoint — retrieves and seeds roles using non-multitenancy services (orgId from request, not Hibernate session) |
| `TokenExchangeResource` | RFC 8693 token exchange — validates subject token, checks subscriptions cross-tenant, and seeds/reads roles using non-multitenancy services (orgId from subject token claims, not Hibernate session) |

## Approved Exceptions (Usage Outside This Package)

The following classes outside this package are permitted to reference `non_multitenancy` types for specific, justified reasons:

| Class | References | Justification |
|-------|-----------|---------------|
| `service/AccountService` | `NonMultitenancyAccountRoleService` | Must assign initial roles during account creation before the orgId is known to Hibernate |
| `service/ClientAllowedRoleService` | `NonMultitenancyOAuthClientService`, `NonMultitenancyAccountRoleService` | Must look up client owner org cross-tenant and remove AccountRole rows across all orgs when a catalog role is deleted or un-shared |
| `boundary/api/ClientRolesResource` | `NonMultitenancyOAuthClientService`, `NonMultitenancySubscriptionService` | Must look up target public clients across all orgs and verify caller's org has a subscription before assigning client-to-client roles |
| `boundary/oauth/AuthorizationResource` | `NonMultitenancyAuthorizationService` | OAuth authorization flow requires cross-tenant client lookup before a tenant session exists |
| `boundary/oauth/OrgSelectionResource` | `NonMultitenancyAuthorizationService` | Org selection step of the auth flow — tenant context not yet established |
| `boundary/oauth/GoogleCallbackResource` | `NonMultitenancyAuthorizationService` | Federated login callback — orgId resolved from external identity, not Hibernate session |
| `boundary/oauth/MicrosoftCallbackResource` | `NonMultitenancyAuthorizationService` | Federated login callback — orgId resolved from external identity, not Hibernate session |
| `boundary/api/AccountsResource` | `NonMultitenancyAccountRoleService` | Seed default roles for all subscribed clients when adding an existing account to an organization (orgId from JWT, not yet known to Hibernate session) |

Any new usage outside this package must be added to the above table with a justification, and approved by the chief architect.

### Security Requirements for Boundary Endpoints

All boundary endpoints MUST:

1. **Require explicit `orgId` verification** - Never rely solely on JWT-resolved tenant context
2. **Verify subscription relationships** - When accessing cross-org data, verify the caller's org has an active subscription
3. **Use `@VerifyOrgMembership`** - Apply the interceptor to validate org membership
4. **Document cross-tenant nature** - Include clear OpenAPI documentation noting the cross-tenant behavior