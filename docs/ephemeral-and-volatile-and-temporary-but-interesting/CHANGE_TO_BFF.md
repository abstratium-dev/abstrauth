# Migration to Backend For Frontend (BFF) Architecture

## Executive Summary

This document outlines the comprehensive changes required to transform the abstrauth authorization server's Angular frontend from a **public OAuth client** (handling tokens in the browser) to a **Backend For Frontend (BFF)** architecture where the Quarkus backend acts as a confidential OAuth client.

## Background

### Current Architecture (Insecure)

Currently, the Angular SPA:
1. Generates PKCE parameters in the browser
2. Initiates OAuth flow by redirecting to `/oauth2/authorize`
3. Receives authorization code in callback
4. **Exchanges code for JWT token in the browser** (`auth-callback.component.ts`)
5. **Stores JWT in memory** and parses it client-side
6. **Sends JWT in Authorization header** to backend APIs via `auth.interceptor.ts`

**Security Issues:**
- JWT token (including signature) is exposed to JavaScript
- Vulnerable to XSS attacks - malicious code can steal tokens
- Violates [draft-ietf-oauth-browser-based-apps-26](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps-26) recommendations
- Not suitable for sensitive applications or applications handling personal data

### Target Architecture (Secure BFF)

With BFF pattern:
1. Angular SPA initiates OAuth flow (redirects to `/oauth2/authorize`)
2. User authenticates and consents
3. **Quarkus backend receives authorization code** (not the browser)
4. **Quarkus exchanges code for tokens** using OIDC extension
5. **Quarkus stores tokens in encrypted HTTP-only cookies**
6. Angular SPA **never sees the JWT signature**
7. Angular fetches token payload (claims) from backend endpoint `/api/auth/userinfo`
8. All API requests automatically include HTTP-only cookie (no interceptor needed)

**Security Benefits:**
- JWT tokens never exposed to JavaScript
- XSS attacks cannot steal tokens
- HTTP-only cookies immune to `document.cookie` access
- Compliant with OAuth 2.0 security best practices
- Suitable for sensitive applications

## Key Changes Overview

### 1. Add Quarkus OIDC Extension
- Add `quarkus-oidc` dependency to `pom.xml`
- Configure Quarkus to use itself as the OIDC provider
- Enable authorization code flow with PKCE

### 2. Backend Changes
- Configure OIDC to point to abstrauth's own endpoints
- Create new endpoint `/api/auth/userinfo` to return JWT payload (without signature)
- Remove manual JWT verification (OIDC extension handles it)
- Update security configuration to use OIDC session cookies

### 3. Frontend Changes
- Remove token exchange logic from `auth-callback.component.ts`
- Update `auth.service.ts` to fetch user info from backend
- **Remove `auth.interceptor.ts`** (cookies sent automatically)
- Update `auth.guard.ts` to check session with backend

### 4. Documentation Updates
- Update all references to "public clients" → "confidential clients"
- Add BFF architecture diagrams
- Update security documentation
- Update client-example to demonstrate BFF pattern

### 5. Testing Updates
- Update Java tests to use OIDC test support
- Update Angular tests (no more JWT parsing)
- Update e2e tests to work with cookie-based auth

## Detailed Changes

### 1. Maven Dependencies (`pom.xml`)

**Add:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-oidc</artifactId>
</dependency>
```

**Keep existing:**
- `quarkus-smallrye-jwt` - Still needed for generating tokens as authorization server
- `quarkus-smallrye-jwt-build` - Still needed for token generation

### 2. Application Configuration (`application.properties`)

**Add OIDC Configuration:**

```properties
# OIDC Configuration - Use abstrauth as its own OIDC provider
quarkus.oidc.auth-server-url=http://localhost:8080
%test.quarkus.oidc.auth-server-url=http://localhost:8080
%e2e.quarkus.oidc.auth-server-url=http://localhost:8080
%prod.quarkus.oidc.auth-server-url=https://auth.abstratium.dev

# Client credentials for abstrauth's own Angular UI
quarkus.oidc.client-id=abstratium-abstrauth
quarkus.oidc.credentials.secret=${ABSTRAUTH_CLIENT_SECRET:dev-secret-CHANGE-IN-PROD}

# Application type: web-app (authorization code flow)
quarkus.oidc.application-type=web-app

# Enable PKCE (required)
quarkus.oidc.authentication.pkce-required=true

# Scopes to request
quarkus.oidc.authentication.scopes=openid,profile,email

# Redirect paths
quarkus.oidc.authentication.redirect-path=/auth-callback
quarkus.oidc.authentication.restore-path-after-redirect=true

# Cookie configuration (HTTP-only, secure)
quarkus.oidc.token-state-manager.strategy=id-refresh-tokens
quarkus.oidc.token-state-manager.split-tokens=true
quarkus.oidc.token-state-manager.encryption-required=true
quarkus.oidc.token-state-manager.encryption-secret=${COOKIE_ENCRYPTION_SECRET:dev-encryption-key-32-chars-min}

# Session timeout (match token expiry)
quarkus.oidc.authentication.session-age-extension=PT1H

# Cookie settings
quarkus.oidc.authentication.cookie-path=/
quarkus.oidc.authentication.cookie-same-site=strict
%prod.quarkus.oidc.authentication.cookie-secure=true

# Logout configuration
quarkus.oidc.logout.path=/api/auth/logout
quarkus.oidc.logout.post-logout-path=/
```

**Update JWT Configuration:**

The existing JWT configuration remains for **token generation** (as authorization server):

```properties
# JWT Signing (for authorization server role)
smallrye.jwt.sign.key=...
smallrye.jwt.new-token.signature-algorithm=PS256

# JWT Verification (for resource server role - API endpoints)
mp.jwt.verify.publickey=...
mp.jwt.verify.publickey.algorithm=PS256
mp.jwt.verify.issuer=https://abstrauth.abstratium.dev

# Map groups claim to roles
smallrye.jwt.path.groups=groups
smallrye.jwt.claims.groups=groups
```

**Note:** We need BOTH:
- OIDC for the Angular UI (BFF pattern)
- JWT for API endpoints (resource server)

### 3. Database Changes

**Add client_secret column:**

Create migration: `V01.XXX__add_client_secret.sql`

```sql
ALTER TABLE T_oauth_clients 
ADD COLUMN client_secret VARCHAR(255);

-- Update default client with secret
UPDATE T_oauth_clients 
SET client_secret = '$2a$10$...' -- bcrypt hash of dev-secret
WHERE client_id = 'abstratium-abstrauth';
```

**Update default client insert:**

Update `V01.006__insertDefaultClient.sql`:

```sql
INSERT INTO T_oauth_clients (id, client_id, client_name, redirect_uris, client_secret, created_at, updated_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440000',
    'abstratium-abstrauth',
    'Abstrauth Admin UI',
    'http://localhost:8080/auth-callback,https://auth.abstratium.dev/auth-callback',
    '$2a$10$...', -- bcrypt hash
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
```

### 4. Backend Java Changes

#### 4.1 New Endpoint: UserInfoResource.java

Create `/api/auth/userinfo` endpoint to return JWT payload without signature:

```java
package dev.abstratium.abstrauth.boundary.auth;

import io.quarkus.oidc.IdToken;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.HashMap;
import java.util.Map;

@Path("/api/auth")
@Authenticated
public class UserInfoResource {

    @Inject
    @IdToken
    JsonWebToken idToken;

    /**
     * Returns JWT payload (claims) without the signature.
     * This is safe to expose to the Angular SPA.
     */
    @GET
    @Path("/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getUserInfo() {
        Map<String, Object> userInfo = new HashMap<>();
        
        userInfo.put("iss", idToken.getIssuer());
        userInfo.put("sub", idToken.getSubject());
        userInfo.put("email", idToken.getClaim("email"));
        userInfo.put("email_verified", idToken.getClaim("email_verified"));
        userInfo.put("name", idToken.getName());
        userInfo.put("groups", idToken.getGroups());
        userInfo.put("scope", idToken.getClaim("scope"));
        userInfo.put("iat", idToken.getIssuedAtTime());
        userInfo.put("exp", idToken.getExpirationTime());
        userInfo.put("client_id", idToken.getClaim("client_id"));
        userInfo.put("jti", idToken.getClaim("jti"));
        userInfo.put("upn", idToken.getClaim("upn"));
        userInfo.put("auth_method", idToken.getClaim("auth_method"));
        userInfo.put("isAuthenticated", true);
        
        return userInfo;
    }

    /**
     * Check if user is authenticated.
     * Returns 200 if authenticated, 401 if not.
     */
    @GET
    @Path("/check")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Boolean> checkAuth() {
        return Map.of("authenticated", true);
    }
}
```

#### 4.2 Update Token Endpoint

The token endpoint must now support confidential clients:

```java
// In TokenResource.java

// Add client authentication
if (clientSecret != null && !clientSecret.isEmpty()) {
    // Validate client_secret
    OAuthClient client = clientService.findByClientId(clientId);
    if (client == null || !passwordService.verifyPassword(clientSecret, client.getClientSecret())) {
        return Response.status(Response.Status.UNAUTHORIZED)
            .entity(Map.of("error", "invalid_client"))
            .build();
    }
}
```

#### 4.3 Remove Manual JWT Handling from Protected Endpoints

Endpoints like `/api/clients` can now rely on OIDC session:

```java
@Path("/api/clients")
@Authenticated  // OIDC handles this
@RolesAllowed("abstratium-abstrauth_manage-clients")
public class ClientsResource {
    // No changes needed - OIDC handles authentication
}
```

### 5. Frontend Angular Changes

#### 5.1 Remove auth-callback.component.ts

**IMPORTANT:** With Quarkus OIDC handling the callback, the Angular auth-callback component is **no longer needed**.

**Flow:**
1. User authenticates → Quarkus OIDC receives callback at `/auth-callback`
2. Quarkus exchanges code for tokens, sets HTTP-only cookies
3. Quarkus redirects to Angular app root `/`
4. Angular APP_INITIALIZER loads userinfo from `/api/userinfo`
5. User is authenticated

**Delete:** `auth-callback.component.ts`, `auth-callback.component.html`, `auth-callback.component.scss`

#### 5.2 Update auth.service.ts

**Remove:**
- `setAccessToken()` method
- `getJwt()` method
- JWT parsing logic
- In-memory JWT storage

**Add:**
```typescript
/**
 * Initialize auth service by loading user info from backend.
 * Called by APP_INITIALIZER before app starts.
 */
initialize(): Observable<void> {
    if (this.initialized) {
        return of(void 0);
    }

    return this.http.get<Token>('/api/userinfo').pipe(
        tap(token => {
            this.token = token;
            this.token$.set(token);
            this.initialized = true;
            this.setupTokenExpiryTimer(token.exp);
        }),
        catchError(() => {
            // Not authenticated - use ANONYMOUS token
            this.token = ANONYMOUS;
            this.token$.set(ANONYMOUS);
            this.initialized = true;
            return of(ANONYMOUS);
        }),
        map(() => void 0)
    );
}

/**
 * Reload user info from backend.
 * Used after login to fetch updated token data.
 */
loadUserInfo(): Observable<Token> {
    return this.http.get<Token>('/api/userinfo').pipe(
        tap(token => {
            this.token = token;
            this.token$.set(token);
            this.setupTokenExpiryTimer(token.exp);
        })
    );
}
```

#### 5.3 Update app.config.ts

**Add APP_INITIALIZER** to load userinfo before app starts:

```typescript
import { APP_INITIALIZER, ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';

import { routes } from './app.routes';
import { AuthService } from './auth.service';

/**
 * Initialize AuthService before app starts.
 * Loads user info from backend if OIDC session exists.
 */
function initializeAuth(authService: AuthService) {
  return () => authService.initialize();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(), // No interceptor needed - cookies sent automatically
    provideAnimations(),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAuth,
      deps: [AuthService],
      multi: true
    }
  ]
};
```

**Remove:** `authInterceptor` import and usage (HTTP-only cookies sent automatically)

#### 5.4 auth.guard.ts - No Changes Needed! ✅

The guard already checks `authService.isAuthenticated()` which uses the loaded token.
No backend calls needed - just checks if userinfo was loaded during initialization.

```typescript
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Redirect to authorize page (which will redirect to signin)
  router.navigate(['/authorize']);
  return false;
};
```

**Why no changes?** The guard checks the token loaded by APP_INITIALIZER. If the session expires, the backend will return 401 and the user will be redirected to login.

#### 5.5 Update authorize.component.ts

**Remove:**
- PKCE generation (Quarkus OIDC handles it)
- State generation (Quarkus OIDC handles it)
- sessionStorage usage

**Simplify to:**
```typescript
initiateOAuthFlow() {
    // Just redirect to Quarkus OIDC login endpoint
    // Quarkus will handle PKCE, state, and redirect to /oauth2/authorize
    window.location.href = '/api/auth/login';
}
```

**Or keep existing flow** if we want Angular to control the authorize endpoint directly. The key is that token exchange happens on backend.

### 6. Documentation Updates

#### 6.1 Update docs/security/SECURITY.md

**Replace section "Angular HTTP Interceptor":**

```markdown
## Backend For Frontend (BFF) Architecture

The Angular frontend uses the Quarkus backend as a BFF (Backend For Frontend):

**Architecture**:
- Angular SPA initiates OAuth flow
- Quarkus backend acts as confidential OAuth client
- Quarkus exchanges authorization code for tokens
- Tokens stored in encrypted HTTP-only cookies
- Angular fetches user info from `/api/auth/userinfo` (JWT payload without signature)

**Security Benefits**:
- JWT tokens never exposed to JavaScript
- XSS attacks cannot steal tokens
- HTTP-only cookies immune to document.cookie access
- Compliant with OAuth 2.0 security best practices

**Implementation**: 
- Backend: `UserInfoResource.java`
- Frontend: `auth.service.ts`
- No HTTP interceptor needed (cookies sent automatically)
```

#### 6.2 Update docs/oauth/FLOWS.md

**Replace "Flow 1: Authorization Code Flow with PKCE (for SPAs)":**

```markdown
## Flow 1: Authorization Code Flow with PKCE (Backend For Frontend)

**Use Case:** Single Page Applications using a Backend For Frontend (BFF)

**Security:** BFF acts as confidential client, tokens never exposed to browser

**Important:** According to [draft-ietf-oauth-browser-based-apps-26](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps-26#section-6.1), 
the BFF pattern is recommended for sensitive applications and applications handling personal data.

### Architecture

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Angular   │ ◄─────► │   Quarkus    │ ◄─────► │  Abstrauth  │
│     SPA     │         │     BFF      │         │  (itself)   │
│             │         │              │         │             │
│ - UI only   │         │ - OIDC client│         │ - Issues    │
│ - No tokens │         │ - HTTP-only  │         │   tokens    │
│             │         │   cookies    │         │             │
└─────────────┘         └──────────────┘         └─────────────┘
```

**All clients using abstrauth MUST be confidential clients with a BFF.**
See [decisions/BFF.md](../../decisions/BFF.md) for rationale.
```

**Update all references:**
- "public client" → "confidential client with BFF"
- "SPA stores tokens" → "BFF stores tokens in HTTP-only cookies"
- "localStorage/sessionStorage" → "HTTP-only encrypted cookies"

#### 6.3 Update client-example/README.md

**Major rewrite** to demonstrate BFF pattern with encrypted cookies instead of session storage.

Key changes:
- Server must be confidential client with `client_secret`
- Server exchanges code for tokens (not browser)
- Server stores tokens in encrypted cookie
- Browser never sees JWT signature
- Remove all references to PKCE in browser (server handles it)

### 7. Client Example Changes

#### 7.1 Update server.js

**Add cookie encryption:**

```javascript
const crypto = require('crypto');

// Encryption key for cookies (32 bytes)
const COOKIE_ENCRYPTION_KEY = process.env.COOKIE_ENCRYPTION_KEY || 
    crypto.randomBytes(32).toString('hex');

function encryptToken(token) {
    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv('aes-256-cbc', 
        Buffer.from(COOKIE_ENCRYPTION_KEY, 'hex'), iv);
    
    let encrypted = cipher.update(token, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    
    return iv.toString('hex') + ':' + encrypted;
}

function decryptToken(encryptedToken) {
    const parts = encryptedToken.split(':');
    const iv = Buffer.from(parts[0], 'hex');
    const encrypted = parts[1];
    
    const decipher = crypto.createDecipheriv('aes-256-cbc',
        Buffer.from(COOKIE_ENCRYPTION_KEY, 'hex'), iv);
    
    let decrypted = decipher.update(encrypted, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    
    return decrypted;
}

// Token exchange endpoint
app.post('/api/token', async (req, res) => {
    const { code, code_verifier } = req.body;
    
    // Exchange code for tokens with client_secret
    const tokenResponse = await fetch(TOKEN_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            grant_type: 'authorization_code',
            code: code,
            redirect_uri: REDIRECT_URI,
            client_id: CLIENT_ID,
            client_secret: CLIENT_SECRET, // Confidential client
            code_verifier: code_verifier
        })
    });
    
    const tokens = await tokenResponse.json();
    
    // Store access token in encrypted HTTP-only cookie
    const encryptedToken = encryptToken(tokens.access_token);
    
    res.cookie('access_token', encryptedToken, {
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'strict',
        maxAge: tokens.expires_in * 1000
    });
    
    res.json({ success: true });
});

// User info endpoint
app.get('/api/user', (req, res) => {
    const encryptedToken = req.cookies.access_token;
    
    if (!encryptedToken) {
        return res.status(401).json({ error: 'Not authenticated' });
    }
    
    try {
        const token = decryptToken(encryptedToken);
        
        // Parse JWT (payload only, no signature verification needed)
        const payload = JSON.parse(
            Buffer.from(token.split('.')[1], 'base64').toString()
        );
        
        res.json(payload);
    } catch (err) {
        res.status(401).json({ error: 'Invalid token' });
    }
});
```

### 8. Testing Changes

#### 8.1 Java Tests

**Use Quarkus OIDC test support:**

```java
@QuarkusTest
@TestHTTPEndpoint(UserInfoResource.class)
public class UserInfoResourceTest {

    @Test
    @TestSecurity(user = "test@example.com", roles = {"abstratium-abstrauth_user"})
    public void testGetUserInfo() {
        given()
            .when()
            .get("/userinfo")
            .then()
            .statusCode(200)
            .body("email", equalTo("test@example.com"));
    }
}
```

#### 8.2 Angular Tests

**Update auth.service.spec.ts:**

```typescript
it('should load user info from backend', (done) => {
    const mockToken: Token = {
        email: 'test@example.com',
        name: 'Test User',
        // ... other fields
    };
    
    httpMock.expectOne('/api/auth/userinfo').flush(mockToken);
    
    service.loadUserInfo().subscribe(token => {
        expect(token.email).toBe('test@example.com');
        done();
    });
});
```

**Remove tests for:**
- JWT parsing
- Token storage
- Interceptor (delete auth.interceptor.spec.ts)

#### 8.3 E2E Tests

**Update to work with cookies:**

```typescript
// In tests-nosignup/happy2.spec.ts

test('should login and access protected pages', async ({ page, context }) => {
    // Navigate to login
    await page.goto('http://localhost:8080/signin');
    
    // Fill credentials
    await page.fill('[name="email"]', 'admin@example.com');
    await page.fill('[name="password"]', 'password');
    await page.click('button[type="submit"]');
    
    // Wait for redirect to callback
    await page.waitForURL('**/auth-callback**');
    
    // Should redirect to accounts page
    await page.waitForURL('**/accounts');
    
    // Check cookies are set
    const cookies = await context.cookies();
    expect(cookies.some(c => c.name.startsWith('q_session'))).toBeTruthy();
    
    // Should be able to access protected API
    const response = await page.request.get('http://localhost:8080/api/clients');
    expect(response.status()).toBe(200);
});
```

## Migration Steps

### Phase 1: Backend Setup
1. Add `quarkus-oidc` dependency
2. Add OIDC configuration to `application.properties`
3. Create database migration for `client_secret`
4. Update default client with secret
5. Create `UserInfoResource.java`
6. Update `TokenResource.java` to support client secrets
7. Run tests: `mvn test`

### Phase 2: Frontend Updates
1. Update `auth-callback.component.ts` (remove token exchange)
2. Update `auth.service.ts` (fetch from backend)
3. Delete `auth.interceptor.ts`
4. Update `auth.guard.ts`
5. Update `authorize.component.ts`
6. Run Angular tests: `cd src/main/webui && npm test`

### Phase 3: Documentation
1. Update `SECURITY.md`
2. Update `FLOWS.md`
3. Update `client-example/README.md`
4. Update `client-example/server.js`

### Phase 4: Testing
1. Update Java integration tests
2. Update Angular unit tests
3. Update e2e tests
4. Run full test suite: `mvn verify`

### Phase 5: Deployment
1. Generate production secrets
2. Update environment variables
3. Deploy and verify

## Security Considerations

### What Changes
- ✅ JWT tokens never exposed to JavaScript
- ✅ HTTP-only cookies prevent XSS token theft
- ✅ Encrypted cookies prevent token inspection
- ✅ PKCE still used (by Quarkus OIDC)
- ✅ State parameter still validated (by Quarkus OIDC)

### What Stays the Same
- ✅ Authorization code flow
- ✅ PKCE protection
- ✅ CSRF protection via state parameter
- ✅ Short-lived authorization codes
- ✅ Token expiration

### Additional Benefits
- ✅ Compliant with OAuth 2.0 security best practices
- ✅ Suitable for sensitive applications
- ✅ Defense in depth against XSS
- ✅ Simplified frontend code

## References

- [OAuth 2.0 for Browser-Based Apps (BFF Section)](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps-26#section-6.1)
- [Quarkus OIDC Code Flow Authentication](https://quarkus.io/guides/security-oidc-code-flow-authentication)
- [RFC 6749 - OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 7636 - PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
- [RFC 9700 - OAuth 2.0 Security Best Practices](https://datatracker.ietf.org/doc/html/rfc9700)
- [decisions/BFF.md](../../decisions/BFF.md)

## Conclusion

This migration transforms abstrauth from a public client architecture (insecure for sensitive applications) to a BFF architecture (secure and compliant with OAuth 2.0 best practices). The changes are comprehensive but necessary to meet security requirements for production use with sensitive data.

All clients using abstrauth must now be confidential clients with a BFF. Public clients (SPAs handling tokens directly) are no longer supported.
