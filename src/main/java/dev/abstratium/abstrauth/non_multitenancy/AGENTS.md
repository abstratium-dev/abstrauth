The entity classes in this package are ONLY TO BE USED when specifically instructed, because they DO NOT conform to the design principles in the document docs/MULTITENANCY_DESIGN.md.

These entity classes do not use the hibernate `@TenantId`.

They can be used in the following circumstances:

- The algorithm needs to create data within an organisation where the JwtOrgResolver class cannot determine the orgId to use because the orgId is not yet known at the time when Hibernate adds it to the session, e.g. when creating an account and assigning initial roles.
- The user needs to see a list of public OAuthClient that their org is subscribed to, even though those clients belong to a different org.