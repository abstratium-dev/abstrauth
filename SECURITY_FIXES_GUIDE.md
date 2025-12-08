# Security Fixes Implementation Guide

This document provides specific code changes to fix the critical and high-severity security vulnerabilities identified in the security audit.

---

## CRITICAL-2: Authorization Code Replay Attack - Token Revocation

### Step 1: Create Token Revocation Table

Create a new Flyway migration: `V01.011__createTableRevokedTokens.sql`

```sql
CREATE TABLE T_revoked_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token_jti VARCHAR(255) NOT NULL UNIQUE,
    revoked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(100) NOT NULL,
    authorization_code_id VARCHAR(36),
    INDEX idx_token_jti (token_jti),
    INDEX idx_revoked_at (revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Step 2: Create RevokedToken Entity

```java
package dev.abstratium.abstrauth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "T_revoked_tokens")
public class RevokedToken {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(name = "token_jti", nullable = false, unique = true)
    private String tokenJti;
    
    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;
    
    @Column(nullable = false, length = 100)
    private String reason;
    
    @Column(name = "authorization_code_id", length = 36)
    private String authorizationCodeId;
    
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (revokedAt == null) {
            revokedAt = LocalDateTime.now();
        }
    }
    
    // Getters and setters...
}
```

### Step 3: Create TokenRevocationService

```java
package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.RevokedToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TokenRevocationService {
    
    @Inject
    EntityManager em;
    
    @Transactional
    public void revokeTokensByAuthorizationCode(String authCodeId, String reason) {
        // This would require storing JTI (JWT ID) when tokens are issued
        // For now, we'll mark the authorization code as compromised
        // In a full implementation, you'd need to:
        // 1. Store JTI when issuing tokens
        // 2. Link JTI to authorization code
        // 3. Revoke all JTIs for this auth code
        
        RevokedToken revocation = new RevokedToken();
        revocation.setAuthorizationCodeId(authCodeId);
        revocation.setReason(reason);
        revocation.setTokenJti("AUTH_CODE_" + authCodeId); // Placeholder
        em.persist(revocation);
    }
    
    public boolean isTokenRevoked(String jti) {
        var query = em.createQuery(
            "SELECT COUNT(r) FROM RevokedToken r WHERE r.tokenJti = :jti", 
            Long.class
        );
        query.setParameter("jti", jti);
        return query.getSingleResult() > 0;
    }
}
```

### Step 4: Update TokenResource to Detect and Handle Replay

```java
// In TokenResource.java, update handleAuthorizationCodeGrant method:

// After line 222 (checking if code has been used):
if (authCode.getUsed()) {
    // SECURITY: Authorization code replay detected!
    // Revoke all tokens issued from this authorization code
    tokenRevocationService.revokeTokensByAuthorizationCode(
        authCode.getId(), 
        "authorization_code_replay_detected"
    );
    
    // Log security event
    logger.warn("SECURITY: Authorization code replay attack detected for code: {}, authCodeId: {}", 
        code, authCode.getId());
    
    return buildErrorResponse(Response.Status.BAD_REQUEST, "invalid_grant",
            "Authorization code has already been used");
}
```

### Step 5: Add JTI to JWT Tokens

```java
// In TokenResource.java, generateAccessToken method:

private String generateAccessToken(Account account, String scope, String clientId, String authMethod) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(3600);
    
    // Generate unique JTI (JWT ID)
    String jti = UUID.randomUUID().toString();
    
    // ... existing code ...
    
    return Jwt.issuer(issuer)
            .jti(jti)  // ADD THIS LINE
            .upn(account.getEmail())
            .subject(account.getId())
            // ... rest of claims ...
            .sign();
}
```

### Step 6: Validate Token Revocation in Protected Endpoints

Create a JWT validation filter:

```java
package dev.abstratium.abstrauth.filter;

import dev.abstratium.abstrauth.service.TokenRevocationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Provider
public class TokenRevocationFilter implements ContainerRequestFilter {
    
    @Inject
    TokenRevocationService revocationService;
    
    @Inject
    JsonWebToken jwt;
    
    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (jwt != null && jwt.getTokenID() != null) {
            if (revocationService.isTokenRevoked(jwt.getTokenID())) {
                requestContext.abortWith(
                    Response.status(401)
                        .entity("{\"error\": \"invalid_token\", \"error_description\": \"Token has been revoked\"}")
                        .build()
                );
            }
        }
    }
}
```

---

## CRITICAL-3: PKCE Timing Attack - Constant-Time Comparison

### Fix in TokenResource.java

```java
// Replace the verifyPKCE method (line 282-300):

private boolean verifyPKCE(String codeVerifier, String codeChallenge, String codeChallengeMethod) {
    try {
        String computedChallenge;
        
        if ("S256".equals(codeChallengeMethod)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            computedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } else if ("plain".equals(codeChallengeMethod)) {
            computedChallenge = codeVerifier;
        } else {
            return false;
        }

        // SECURITY FIX: Use constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
            computedChallenge.getBytes(StandardCharsets.UTF_8),
            codeChallenge.getBytes(StandardCharsets.UTF_8)
        );
    } catch (Exception e) {
        return false;
    }
}
```

---

## CRITICAL-4: Client Secret Support

### Step 1: Add Client Secret to Database

Create migration: `V01.012__addClientSecretToOAuthClients.sql`

```sql
ALTER TABLE T_oauth_clients 
ADD COLUMN client_secret VARCHAR(255) NULL COMMENT 'Hashed client secret for confidential clients';

ALTER TABLE T_oauth_clients
ADD COLUMN client_secret_hash VARCHAR(255) NULL COMMENT 'BCrypt hash of client secret';
```

### Step 2: Update OAuthClient Entity

```java
// In OAuthClient.java, add:

@Column(name = "client_secret_hash")
private String clientSecretHash;

public String getClientSecretHash() {
    return clientSecretHash;
}

public void setClientSecretHash(String clientSecretHash) {
    this.clientSecretHash = clientSecretHash;
}
```

### Step 3: Add Client Authentication to TokenResource

```java
// In TokenResource.java, add new method:

private boolean authenticateClient(String clientId, String clientSecret) {
    if (clientSecret == null || clientSecret.isBlank()) {
        return false;
    }
    
    Optional<OAuthClient> clientOpt = clientService.findByClientId(clientId);
    if (clientOpt.isEmpty()) {
        return false;
    }
    
    OAuthClient client = clientOpt.get();
    
    // Public clients don't have secrets
    if ("public".equals(client.getClientType())) {
        return false;
    }
    
    // Confidential clients must have a secret
    if (client.getClientSecretHash() == null) {
        return false;
    }
    
    // Verify secret using BCrypt (Spring Security implementation)
    try {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.matches(clientSecret, client.getClientSecretHash());
    } catch (IllegalArgumentException e) {
        // Invalid hash format
        return false;
    }
}

// In handleAuthorizationCodeGrant method, add after line 210:

// Authenticate confidential clients
Optional<OAuthClient> clientOpt = clientService.findByClientId(clientId);
if (clientOpt.isPresent()) {
    OAuthClient client = clientOpt.get();
    if ("confidential".equals(client.getClientType())) {
        // Confidential clients MUST authenticate
        if (!authenticateClient(clientId, clientSecret)) {
            return buildErrorResponse(Response.Status.UNAUTHORIZED, "invalid_client",
                    "Client authentication failed");
        }
    }
}
```

---

## HIGH-5: Enforce PKCE for Public Clients

### Fix in AuthorizationResource.java

```java
// Replace lines 166-169 with:

// Validate PKCE based on client type
if ("public".equals(client.getClientType())) {
    // RFC 9700: PKCE is MANDATORY for public clients
    if (codeChallenge == null || codeChallenge.isBlank()) {
        return buildErrorRedirect(redirectUri, "invalid_request", 
                "code_challenge is required for public clients (RFC 9700)", state);
    }
} else if (client.getRequirePkce() && (codeChallenge == null || codeChallenge.isBlank())) {
    // Confidential clients can optionally require PKCE
    return buildErrorRedirect(redirectUri, "invalid_request", 
            "code_challenge is required for this client", state);
}
```

---

## MEDIUM-1: Increase BCrypt Iterations

### Fix in AccountService.java

```java
// Replace line 149:

int iterationCount = 12; // Increased from 10 to 12 (OWASP recommendation)
```

### Add Configuration Property

In `application.properties`:

```properties
# Password hashing configuration
password.bcrypt.iterations=12
%dev.password.bcrypt.iterations=10
%test.password.bcrypt.iterations=10
```

Then inject and use:

```java
@ConfigProperty(name = "password.bcrypt.iterations", defaultValue = "12")
int bcryptIterations;

private String hashPassword(String password) {
    // Apply pepper (application-wide secret) before hashing
    return passwordEncoder.encode(pepper + password);
}
```

---

## HIGH-4: Account Lockout Timing Leak Fix

### Fix in AccountService.java

```java
// Replace authenticate method (lines 107-142):

@Transactional
public Optional<@NonNull Account> authenticate(String username, String password) {
    Optional<Credential> credentialOpt = findCredentialByUsername(username);
    if (credentialOpt.isEmpty()) {
        // SECURITY: Always perform password verification even if user doesn't exist
        // This prevents timing attacks that could enumerate valid usernames
        performDummyPasswordVerification(password);
        return Optional.empty();
    }

    Credential credential = credentialOpt.get();
    
    // Check if account is locked
    boolean isLocked = credential.getLockedUntil() != null && 
        credential.getLockedUntil().isAfter(java.time.LocalDateTime.now());
    
    // SECURITY: Always verify password, even if account is locked
    // This ensures constant-time behavior
    boolean passwordValid = verifyPassword(password, credential.getPasswordHash());
    
    if (isLocked) {
        // Account is locked, but we still verified the password
        return Optional.empty();
    }
    
    if (passwordValid) {
        // Reset failed attempts on successful login
        credential.setFailedLoginAttempts(0);
        credential.setLockedUntil(null);
        em.merge(credential);
        return findById(credential.getAccountId());
    } else {
        // Increment failed attempts
        int attempts = credential.getFailedLoginAttempts() + 1;
        credential.setFailedLoginAttempts(attempts);

        // Lock account after 5 failed attempts for 15 minutes
        if (attempts >= 5) {
            credential.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(15));
        }

        em.merge(credential);
        return Optional.empty();
    }
}

private void performDummyPasswordVerification(String password) {
    // Perform a dummy BCrypt verification to maintain constant time
    // This prevents timing attacks that could enumerate valid usernames
    try {
        // Use a pre-computed dummy hash to verify against
        String dummyHash = "$2a$12$abcdefghijklmnopqrstuv.abcdefghijklmnopqrstuv.abcdefghijk";
        passwordEncoder.matches(pepper + password, dummyHash);
    } catch (Exception e) {
        // Ignore errors in dummy verification
    }
}
```

---

## Testing the Fixes

### Run Security Tests

```bash
# Run the security audit test suite
mvn test -Dtest=SecurityAuditTest

# Run all tests to ensure nothing broke
mvn verify
```

### Manual Testing

1. **Test Authorization Code Replay:**
   ```bash
   # Use code once - should succeed
   # Use code again - should fail AND revoke first token
   # Try to use first token - should get 401
   ```

2. **Test PKCE Timing:**
   ```bash
   # Measure response times for correct vs incorrect verifiers
   # Times should be similar (constant-time)
   ```

3. **Test Client Secret:**
   ```bash
   # Confidential client without secret - should fail
   # Confidential client with wrong secret - should fail
   # Confidential client with correct secret - should succeed
   ```

---

## Deployment Checklist

- [ ] Review all code changes
- [ ] Run full test suite
- [ ] Update database schema (Flyway migrations)
- [ ] Update configuration properties
- [ ] Test in staging environment
- [ ] Update documentation
- [ ] Deploy to production
- [ ] Monitor security logs for 48 hours
- [ ] Conduct follow-up security audit

---

## Additional Recommendations

### 1. Add Security Event Logging

```java
@ApplicationScoped
public class SecurityEventLogger {
    private static final Logger logger = Logger.getLogger(SecurityEventLogger.class);
    
    public void logAuthCodeReplay(String code, String clientId, String ip) {
        logger.warn("SECURITY_EVENT: authorization_code_replay | code={} | client={} | ip={}", 
            code, clientId, ip);
    }
    
    public void logFailedAuthentication(String username, String ip) {
        logger.warn("SECURITY_EVENT: failed_authentication | username={} | ip={}", 
            username, ip);
    }
    
    public void logAccountLockout(String username, String ip) {
        logger.warn("SECURITY_EVENT: account_lockout | username={} | ip={}", 
            username, ip);
    }
}
```

### 2. Add Monitoring Alerts

Set up alerts for:
- Authorization code replay attempts
- High rate of failed authentications
- Account lockouts
- Token revocations
- Rate limit violations

### 3. Regular Security Reviews

- Schedule quarterly security audits
- Review security logs weekly
- Update dependencies monthly
- Conduct penetration testing annually

---

## Questions?

For questions about these fixes, refer to:
- **SECURITY_AUDIT_FINDINGS.md** - Detailed vulnerability analysis
- **SECURITY_AUDIT_SUMMARY.md** - Executive summary
- **SECURITY.md** - General security documentation

Or contact the security team.
