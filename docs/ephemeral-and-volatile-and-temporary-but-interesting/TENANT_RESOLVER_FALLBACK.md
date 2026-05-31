## Fixing "SessionFactory configured for multi-tenancy, but no tenant identifier specified"

### Problem

When running `@QuarkusTest` service tests with discriminator-based multitenancy, you may encounter:

```
org.hibernate.HibernateException: SessionFactory configured for multi-tenancy, but no tenant identifier specified
```

This occurs when:
- Your `TenantResolver` tries to access the HTTP request context
- No HTTP request exists (e.g., in `@QuarkusTest` service tests)
- The resolver throws instead of returning a fallback tenant ID

### Root Cause

In `@QuarkusTest` service tests (like `OAuthClientServiceTest`), there is no active HTTP request. If your `TenantResolver` injects `HttpServerRequest` and tries to access it without a try-catch fallback, Hibernate cannot determine the tenant ID.

### Solution: Always Provide a Fallback

Your `TenantResolver` must implement `getDefaultTenantId()` and catch exceptions in `resolveTenantId()`:

```java
@PersistenceUnitExtension
@RequestScoped
public class YourTenantResolver implements TenantResolver {
    
    public static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000000";
    
    @Inject
    HttpServerRequest request;

    @Override
    public String getDefaultTenantId() {
        return DEFAULT_TENANT;  // Required fallback
    }

    @Override
    public String resolveTenantId() {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return DEFAULT_TENANT;
            }
            // Extract tenant from JWT...
            String tenant = extractFromJwt(authHeader);
            return tenant != null ? tenant : DEFAULT_TENANT;
        } catch (Exception e) {
            // KEY: This catch block is essential for tests to work!
            // When no HTTP request context exists, return default
            return DEFAULT_TENANT;
        }
    }
}
```

### Key Implementation Details

#### 1. Required Annotations
```java
@PersistenceUnitExtension  // Required for Hibernate to discover the resolver
@RequestScoped            // Scoped to HTTP request (null in tests, caught by try-catch)
```

#### 2. Required Configuration
```properties
# application.properties
quarkus.hibernate-orm.multitenant=DISCRIMINATOR
```

#### 3. The Critical Try-Catch Pattern

The abstrauth `JwtOrgResolver` works in tests because of lines 38-53:

```java
@Override
public String resolveTenantId() {
    try {
        // This will throw when no HTTP request exists
        String authHeader = request.getHeader("Authorization");
        // ... extract tenant from JWT
    } catch (Exception e) {
        // Fallback for tests and public endpoints
        return DEFAULT_ORG_ID;
    }
    return DEFAULT_ORG_ID;
}
```

Without the try-catch, the exception propagates to Hibernate, causing:
```
HibernateException: SessionFactory configured for multi-tenancy, but no tenant identifier specified
```

### How abstrauth Tests Work

Service tests like `OAuthClientServiceTest` work because:

1. Test calls `oauthClientService.create(client)`
2. Hibernate calls `JwtOrgResolver.resolveTenantId()`
3. No HTTP request exists → `request.getHeader()` throws
4. Catch block returns `DEFAULT_ORG_ID`
5. Entity is created with `org_id = '00000000-0000-0000-0000-000000000000'`
6. Test succeeds

### Common Mistakes

| Mistake | Result |
|---------|--------|
| No try-catch in `resolveTenantId()` | `HibernateException` in tests |
| Not implementing `getDefaultTenantId()` | Compilation error or runtime failure |
| Missing `@PersistenceUnitExtension` | Resolver not discovered by Hibernate |
| Returning `null` from `resolveTenantId()` | `HibernateException` |

### Testing Cross-Org Isolation

When you need to test with data in specific orgs, use native SQL (bypasses tenant filter):

```java
@Inject
EntityManager em;

// Insert with explicit org_id
em.createNativeQuery(
    "INSERT INTO T_oauth_clients (id, client_id, org_id) VALUES (:id, :clientId, :orgId)")
    .setParameter("id", uuid)
    .setParameter("clientId", clientId)
    .setParameter("orgId", "specific-org-id")
    .executeUpdate();
```

### Relevant Files

- `@/shared2/abstratium/github.com/abstrauth/src/main/java/dev/abstratium/abstrauth/service/JwtOrgResolver.java` - Working implementation with fallback
- `@/shared2/abstratium/github.com/abstrauth/src/test/java/dev/abstratium/abstrauth/service/OAuthClientServiceTest.java` - Service test that relies on fallback
- `@/shared2/abstratium/github.com/abstrauth/src/test/java/dev/abstratium/abstrauth/boundary/MultiTenancySecurityTest.java` - Cross-org tests using native SQL
