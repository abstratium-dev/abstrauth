# Service Account Roles in Client Credentials Flow

## Problem Statement

When implementing OAuth 2.0 Client Credentials flow for service-to-service authentication, we need to determine how to assign **roles** to service accounts (clients) so that these roles can be included in the JWT access token.

### Current Situation

- **User Authentication**: Users (`Account` entity) have roles assigned via `AccountRole` table, which references both `account_id` and `client_id`
- **Service Authentication**: Services authenticate using `client_id` + `client_secret`, but there's no mechanism to assign roles to the service itself
- **The Gap**: When a service obtains a token via client credentials, what roles should be in that token?

## Industry Standard Solutions

After researching RFC 6749, Microsoft Entra ID (Azure AD), Auth0, Keycloak, and Okta, here are the standard approaches:

### 1. **Scopes (Not Roles)** - The OAuth 2.0 Standard Approach

**RFC 6749 Section 4.4** specifies that client credentials flow uses **scopes**, not roles:

- **Scopes** represent permissions/capabilities (e.g., `api:read`, `api:write`, `users:manage`)
- Scopes are **pre-configured** on the client at registration time
- The service requests scopes when obtaining a token
- The authorization server validates that requested scopes are within the allowed scopes
- The token contains the granted scopes in the `scope` claim

**This is what abstrauth's design document already proposes** in `SERVICE_TO_SERVICE_AUTH.md`.

```json
{
  "sub": "service-client-id",
  "client_id": "service-client-id",
  "scope": "api:read api:write",
  "aud": "api.abstratium.dev",
  "iss": "https://auth.abstratium.dev",
  "exp": 1234567890
}
```

**Key Point**: In standard OAuth 2.0, **scopes are the authorization mechanism for client credentials**, not roles.

### 2. **Service Account Roles** - The Keycloak Approach

Keycloak extends the standard by supporting **service account roles**:

- Each confidential client can have "Service Accounts Enabled"
- The client gets a **virtual service account user**
- Roles can be assigned to this service account in the "Service Account Roles" tab
- When the client uses client credentials flow, the token includes these roles

**How it works**:
1. Client authenticates with `client_id` + `client_secret`
2. Keycloak looks up the service account associated with that client
3. Keycloak retrieves roles assigned to that service account
4. Token includes both scopes AND roles (as `groups` or `roles` claim)

### 3. **Application Permissions** - The Microsoft Entra ID Approach

Microsoft uses **application permissions** (also called app roles):

- APIs expose "application permissions" (e.g., `Mail.Read.All`, `Directory.Read.All`)
- Admin grants these permissions to the M2M application
- Permissions are stored as **app role assignments**
- Token includes these as `roles` claim

**How it works**:
1. Admin assigns app roles to the service application
2. Service authenticates with client credentials
3. Token includes `roles` claim with assigned app roles

### 4. **Comparison Table**

| Approach | Mechanism | Where Configured | Token Claim | Flexibility |
|----------|-----------|------------------|-------------|-------------|
| **OAuth 2.0 Standard** | Scopes | Client registration | `scope` | ✅ Standard, simple |
| **Keycloak** | Service Account + Roles | Service account roles tab | `groups`/`roles` | ✅ Familiar role model |
| **Microsoft Entra** | Application Permissions | App role assignments | `roles` | ✅ Fine-grained |
| **Auth0** | Scopes + Hooks | API permissions | `scope` + custom | ⚠️ Requires hooks for roles |

## Recommended Solution for Abstrauth

### Option A: **Scopes Only** (Recommended - Standard Compliant)

**Use scopes as the authorization mechanism**, as already designed in `SERVICE_TO_SERVICE_AUTH.md`.

#### Why This is Correct

1. **RFC 6749 Compliant**: The OAuth 2.0 spec uses scopes for client credentials
2. **Industry Standard**: Most OAuth servers use scopes for M2M authorization
3. **Already Designed**: Your design document already has this approach
4. **Simpler**: No need for a separate role system for services

#### Implementation

**Database**: Use existing `allowed_scopes` column in `T_oauth_clients`

```sql
-- Already exists in your schema
ALTER TABLE T_oauth_clients 
  ADD COLUMN allowed_scopes VARCHAR(500);
```

**Token Generation**: Include scopes in token (no roles needed)

```java
private String generateServiceAccessToken(String clientId, Set<String> grantedScopes) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(3600); // 1 hour

    return Jwt.issuer(issuer)
            .subject(clientId)  // Service ID as subject
            .claim("client_id", clientId)
            .claim("scope", String.join(" ", grantedScopes))
            .claim("jti", UUID.randomUUID().toString())
            .audience(audience)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .jws()
                .keyId("abstrauth-key-1")
            .sign();
}
```

**Authorization in Resource Servers**: Check scopes, not roles

```java
@GET
@Path("/api/resource")
public Response getResource(@Context SecurityContext securityContext) {
    JsonWebToken jwt = (JsonWebToken) securityContext.getUserPrincipal();
    
    // Check if token has required scope
    String scope = jwt.getClaim("scope");
    if (scope == null || !scope.contains("api:read")) {
        return Response.status(403)
            .entity(Map.of("error", "insufficient_scope"))
            .build();
    }
    
    return Response.ok(data).build();
}
```

#### Scope Design

Use **fine-grained scopes** that map to permissions:

```
api:read              - Read access to APIs
api:write             - Write access to APIs
clients:read          - Read OAuth clients
clients:manage        - Manage OAuth clients
accounts:read         - Read user accounts
accounts:manage       - Manage user accounts
roles:assign          - Assign roles to users
```

**Mapping to User Roles**: If you need to map scopes to user-style roles:

```
admin role → clients:manage accounts:manage roles:assign
viewer role → api:read clients:read accounts:read
service role → api:read api:write
```

### Option B: **Service Account Roles** (Keycloak-Style)

If you **really need** roles (not scopes) for services, implement a service account system.

#### Database Schema

Create a new table for service account roles:

```sql
CREATE TABLE T_service_account_roles (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_service_roles_client 
        FOREIGN KEY (client_id) 
        REFERENCES T_oauth_clients(client_id) 
        ON DELETE CASCADE,
    UNIQUE INDEX I_service_roles_unique (client_id, role)
);
```

#### Token Generation

```java
private String generateServiceAccessToken(String clientId, Set<String> grantedScopes) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(3600);

    // Get roles for this service client
    Set<String> serviceRoles = serviceAccountRoleService.findRolesByClientId(clientId);
    Set<String> groups = new HashSet<>();
    for (String role : serviceRoles) {
        groups.add(clientId + "_" + role);  // Same format as user roles
    }

    return Jwt.issuer(issuer)
            .subject(clientId)
            .claim("client_id", clientId)
            .claim("scope", String.join(" ", grantedScopes))
            .groups(groups)  // Add roles as groups
            .claim("jti", UUID.randomUUID().toString())
            .audience(audience)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .jws()
                .keyId("abstrauth-key-1")
            .sign();
}
```

#### When to Use This

- You have **existing role-based authorization** in resource servers
- You want **consistent authorization model** between users and services
- You need **dynamic role assignment** (not just static scopes)

#### Drawbacks

- ❌ Not standard OAuth 2.0 (custom extension)
- ❌ More complex (additional table, service)
- ❌ Mixing concerns (scopes AND roles)

### Option C: **Hybrid Approach** (Most Flexible)

Use **both scopes and roles**:

- **Scopes**: Coarse-grained API access (`api:read`, `api:write`)
- **Roles**: Fine-grained permissions within the client context

```json
{
  "sub": "monitoring-service",
  "client_id": "monitoring-service",
  "scope": "api:read",
  "groups": ["abstratium-abstrauth_metrics-reader", "abstratium-abstrauth_health-checker"],
  "aud": "api.abstratium.dev"
}
```

## Recommendation

### **Use Option B: Service Account Roles** ✅ (UPDATED)

**Critical Requirements Identified**:

1. **@RolesAllowed Compatibility**: Quarkus `@RolesAllowed` annotation checks the `groups` claim in JWT tokens
2. **Audit Logging**: Need `sub` (subject) claim to identify "who" performed an action
3. **Service-to-Service Orchestration**: Payment service → Accounting service requires proper authorization
4. **Consistent Authorization Model**: Resource servers already use `@RolesAllowed` for user tokens

**Why Scopes-Only Doesn't Work**:

❌ `@RolesAllowed("api:read")` won't work because:
- `@RolesAllowed` checks the `groups` claim, not the `scope` claim
- You'd need to rewrite all resource server authorization logic
- Inconsistent with existing user authentication pattern

**Why Service Account Roles IS Correct**:

✅ Services need `groups` claim with roles like:
- `abstratium-abstrauth_service-reader`
- `abstratium-abstrauth_service-writer`
- `payment-service_accounting-writer`

✅ This allows:
- `@RolesAllowed("payment-service_accounting-writer")` to work
- Audit logs to show `sub: "accounting-service-client-id"`
- Consistent authorization model across users and services

### Implementation Steps

1. ✅ **Already Done**: Database schema has `allowed_scopes` column
2. ✅ **Already Done**: `ClientSecret` table for secret rotation
3. **TODO**: Create `T_service_account_roles` table
4. **TODO**: Create `ServiceAccountRole` entity and repository
5. **TODO**: Implement client credentials grant in `TokenResource`
6. **TODO**: Generate tokens with BOTH `scope` AND `groups` claims
7. **TODO**: Create UI/API for managing service account roles

## Detailed Implementation

### 1. Database Schema

Create migration file: `V01.015__create_service_account_roles_table.sql`

```sql
CREATE TABLE T_service_account_roles (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_service_roles_client 
        FOREIGN KEY (client_id) 
        REFERENCES T_oauth_clients(client_id) 
        ON DELETE CASCADE,
    UNIQUE INDEX I_service_roles_unique (client_id, role),
    INDEX I_service_roles_client (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 2. Entity Class

```java
@Entity
@Table(name = "T_service_account_roles")
public class ServiceAccountRole {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(nullable = false, length = 100)
    private String role;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    // Getters and setters...
}
```

### 3. Repository

```java
@ApplicationScoped
public class ServiceAccountRoleRepository {
    @Inject
    EntityManager em;

    public Set<String> findRolesByClientId(String clientId) {
        return new HashSet<>(em.createQuery(
            "SELECT r.role FROM ServiceAccountRole r WHERE r.clientId = :clientId",
            String.class)
            .setParameter("clientId", clientId)
            .getResultList());
    }

    public void persist(ServiceAccountRole role) {
        em.persist(role);
    }

    public void deleteByClientIdAndRole(String clientId, String role) {
        em.createQuery(
            "DELETE FROM ServiceAccountRole r WHERE r.clientId = :clientId AND r.role = :role")
            .setParameter("clientId", clientId)
            .setParameter("role", role)
            .executeUpdate();
    }
}
```

### 4. Token Generation for Client Credentials

Update `TokenResource.java`:

```java
@POST
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
public Response token(
    @Context HttpHeaders headers,
    @FormParam("grant_type") String grantType,
    @FormParam("code") String code,
    @FormParam("client_id") String clientId,
    @FormParam("client_secret") String clientSecret,
    @FormParam("scope") String scope,
    // ... other params
) {
    // Extract credentials from Basic Auth if needed
    if ((clientId == null || clientId.isBlank()) && headers.getHeaderString("Authorization") != null) {
        String[] credentials = extractBasicAuth(headers.getHeaderString("Authorization"));
        if (credentials != null) {
            clientId = credentials[0];
            clientSecret = credentials[1];
        }
    }

    // Validate grant_type
    if ("authorization_code".equals(grantType)) {
        return handleAuthorizationCodeGrant(code, redirectUri, clientId, clientSecret, codeVerifier);
    } else if ("client_credentials".equals(grantType)) {
        return handleClientCredentialsGrant(clientId, clientSecret, scope);
    } else if ("refresh_token".equals(grantType)) {
        return buildErrorResponse(Response.Status.BAD_REQUEST, "unsupported_grant_type",
                "Refresh token grant not yet implemented");
    } else {
        return buildErrorResponse(Response.Status.BAD_REQUEST, "unsupported_grant_type",
                "Grant type must be 'authorization_code', 'client_credentials', or 'refresh_token'");
    }
}

private Response handleClientCredentialsGrant(String clientId, String clientSecret, String requestedScope) {
    // 1. Validate client credentials
    Optional<OAuthClient> clientOpt = clientService.findByClientId(clientId);
    if (clientOpt.isEmpty()) {
        return buildErrorResponse(Response.Status.UNAUTHORIZED, "invalid_client",
                "Client not found");
    }

    OAuthClient client = clientOpt.get();

    // 2. Verify this is a SERVICE client (not WEB_APPLICATION)
    if (!"SERVICE".equals(client.getClientType())) {
        return buildErrorResponse(Response.Status.UNAUTHORIZED, "invalid_client",
                "Client credentials grant only allowed for SERVICE clients");
    }

    // 3. Authenticate client with secret
    if (!authenticateClient(client, clientSecret)) {
        return buildErrorResponse(Response.Status.UNAUTHORIZED, "invalid_client",
                "Client authentication failed");
    }

    // 4. Validate and filter requested scopes
    Set<String> allowedScopes = parseScopes(client.getAllowedScopes());
    Set<String> requestedScopes = parseScopes(requestedScope);
    
    // If no scopes requested, grant all allowed scopes
    if (requestedScopes.isEmpty()) {
        requestedScopes = allowedScopes;
    }
    
    // Verify requested scopes are within allowed scopes
    if (!allowedScopes.containsAll(requestedScopes)) {
        return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_scope",
                "Requested scope exceeds client's allowed scopes");
    }

    // 5. Get service account roles
    Set<String> serviceRoles = serviceAccountRoleRepository.findRolesByClientId(clientId);
    Set<String> groups = new HashSet<>();
    for (String role : serviceRoles) {
        // Use same format as user roles: {client_id}_{role}
        groups.add(clientId + "_" + role);
    }

    // 6. Generate service token with BOTH scopes AND groups
    String accessToken = generateServiceAccessToken(clientId, requestedScopes, groups);

    // 7. Return token (no refresh token for client credentials)
    return Response.ok(Map.of(
        "access_token", accessToken,
        "token_type", "Bearer",
        "expires_in", sessionTimeoutSeconds,
        "scope", String.join(" ", requestedScopes)
    )).build();
}

private String generateServiceAccessToken(String clientId, Set<String> scopes, Set<String> groups) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(sessionTimeoutSeconds);

    return Jwt.issuer(issuer)
            .claim("jti", UUID.randomUUID().toString())
            .subject(clientId)  // Service client ID as subject (for audit logging)
            .groups(groups)     // Roles for @RolesAllowed
            .claim("scope", String.join(" ", scopes))  // OAuth 2.0 scopes
            .claim("client_id", clientId)
            .audience(audience)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .jws()
                .keyId("abstrauth-key-1")
            .sign();
}

private Set<String> parseScopes(String scopeString) {
    if (scopeString == null || scopeString.isBlank()) {
        return new HashSet<>();
    }
    return new HashSet<>(Arrays.asList(scopeString.trim().split("\\s+")));
}
```

### 5. Example Token for Service

```json
{
  "iss": "https://auth.abstratium.dev",
  "sub": "payment-service",
  "aud": "api.abstratium.dev",
  "exp": 1234567890,
  "iat": 1234564290,
  "jti": "unique-token-id",
  "client_id": "payment-service",
  "scope": "api:read api:write",
  "groups": [
    "payment-service_accounting-writer",
    "payment-service_transaction-creator"
  ]
}
```

### 6. Resource Server Authorization

Now `@RolesAllowed` works seamlessly:

```java
@Path("/api/accounting")
public class AccountingResource {
    
    @Inject
    JsonWebToken jwt;
    
    @POST
    @Path("/transactions")
    @RolesAllowed("payment-service_accounting-writer")  // Works!
    public Response createTransaction(TransactionRequest request) {
        // Get the subject for audit logging
        String actor = jwt.getSubject();  // "payment-service"
        
        // Log the action
        auditLog.log("Transaction created by: " + actor);
        
        // Check if this is a service or user
        String email = jwt.getClaim("email");
        if (email == null) {
            // This is a service token
            logger.info("Service {} created transaction", actor);
        } else {
            // This is a user token
            logger.info("User {} created transaction", email);
        }
        
        return Response.ok().build();
    }
}
```

### 7. Service Account Role Management API

```java
@Path("/api/clients/{clientId}/roles")
@Produces(MediaType.APPLICATION_JSON)
public class ServiceAccountRolesResource {
    
    @Inject
    ServiceAccountRoleRepository roleRepository;
    
    @GET
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    public Set<String> listRoles(@PathParam("clientId") String clientId) {
        return roleRepository.findRolesByClientId(clientId);
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    @Transactional
    public Response addRole(@PathParam("clientId") String clientId, AddRoleRequest request) {
        ServiceAccountRole role = new ServiceAccountRole();
        role.setClientId(clientId);
        role.setRole(request.role);
        roleRepository.persist(role);
        
        return Response.ok().build();
    }
    
    @DELETE
    @Path("/{role}")
    @RolesAllowed(Roles.MANAGE_CLIENTS)
    @Transactional
    public Response removeRole(@PathParam("clientId") String clientId, @PathParam("role") String role) {
        roleRepository.deleteByClientIdAndRole(clientId, role);
        return Response.noContent().build();
    }
}
```

## Example: Complete Flow

### 1. Create Service Client

```bash
POST /api/clients
{
  "clientName": "Monitoring Service",
  "clientType": "SERVICE",
  "allowedScopes": ["api:read", "metrics:read"]
}
```

Response:
```json
{
  "clientId": "monitoring-service-abc123",
  "clientSecret": "secret_xyz789_STORE_THIS",
  "allowedScopes": ["api:read", "metrics:read"]
}
```

### 2. Service Requests Token

```bash
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id=monitoring-service-abc123
&client_secret=secret_xyz789
&scope=api:read metrics:read
```

Response:
```json
{
  "access_token": "eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "api:read metrics:read"
}
```

### 3. Token Contents

```json
{
  "iss": "https://auth.abstratium.dev",
  "sub": "monitoring-service-abc123",
  "aud": "api.abstratium.dev",
  "exp": 1234567890,
  "iat": 1234564290,
  "jti": "unique-token-id",
  "client_id": "monitoring-service-abc123",
  "scope": "api:read metrics:read"
}
```

**Note**: No `groups`, no `email`, no `name` - just service identity and scopes.

### 4. Resource Server Validates Token

```java
@GET
@Path("/api/metrics")
public Response getMetrics(@Context SecurityContext securityContext) {
    JsonWebToken jwt = (JsonWebToken) securityContext.getUserPrincipal();
    
    // Check if this is a service token (no email claim)
    String email = jwt.getClaim("email");
    if (email == null) {
        // This is a service token, check scopes
        String scope = jwt.getClaim("scope");
        if (!scope.contains("metrics:read")) {
            return Response.status(403).build();
        }
    } else {
        // This is a user token, check roles
        Set<String> groups = jwt.getGroups();
        if (!groups.contains("abstratium-abstrauth_metrics-reader")) {
            return Response.status(403).build();
        }
    }
    
    return Response.ok(metrics).build();
}
```

## Migration Path

If you later decide you need roles for services:

1. Create `T_service_account_roles` table
2. Add `ServiceAccountRoleService`
3. Update token generation to include roles
4. **Maintain backward compatibility**: Tokens still have scopes

## Conclusion (UPDATED)

**Answer to your question**: 

> "How can a service, which is signing in with just a client_id and client_secret, get roles assigned to the token?"

**The answer for Quarkus/MicroProfile JWT applications is: Services MUST get roles (groups claim) to work with @RolesAllowed.**

### Final Recommendation

**Use Option B: Service Account Roles** with the following implementation:

1. **Create `T_service_account_roles` table** to assign roles to `client_id`
2. **Generate tokens with both `scope` AND `groups` claims**:
   - `scope`: OAuth 2.0 standard scopes for API-level authorization
   - `groups`: Roles for `@RolesAllowed` compatibility
3. **Set `sub` claim to `client_id`** for audit logging
4. **Use same role naming convention**: `{client_id}_{role}` (e.g., `payment-service_accounting-writer`)

### Why This is the Correct Approach

1. ✅ **@RolesAllowed works**: `@RolesAllowed("payment-service_accounting-writer")` checks `groups` claim
2. ✅ **Audit logging works**: `sub` claim identifies the service
3. ✅ **Consistent with users**: Same authorization model for users and services
4. ✅ **Industry precedent**: Keycloak, Microsoft Entra ID use this approach
5. ✅ **Flexible**: Can assign different roles to different services

### OAuth 2.0 Compliance Note

This is a **standard extension** to OAuth 2.0:
- RFC 6749 defines the client credentials flow mechanism
- The `groups` claim is part of MicroProfile JWT and OpenID Connect standards
- Microsoft, Keycloak, and other major providers use roles/groups for M2M authorization
- Scopes remain in the token for OAuth 2.0 compliance

## References

- **RFC 6749 Section 4.4**: Client Credentials Grant
- **Keycloak**: Service Account Roles feature
- **Microsoft Entra ID**: Application Permissions (app roles)
- **Auth0**: M2M Applications with API permissions (scopes)
- **Your Design**: `SERVICE_TO_SERVICE_AUTH.md` (already correct!)
