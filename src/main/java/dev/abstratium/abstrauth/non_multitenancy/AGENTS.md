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
| `NonMultitenancyClientsResource` | Access public OAuth clients owned by other organisations that the caller's org subscribes to |

### Security Requirements for Boundary Endpoints

All boundary endpoints MUST:

1. **Require explicit `orgId` verification** - Never rely solely on JWT-resolved tenant context
2. **Verify subscription relationships** - When accessing cross-org data, verify the caller's org has an active subscription
3. **Use `@VerifyOrgMembership`** - Apply the interceptor to validate org membership
4. **Document cross-tenant nature** - Include clear OpenAPI documentation noting the cross-tenant behavior