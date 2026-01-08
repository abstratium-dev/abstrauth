# CSRF Protection Implementation Guide

This document describes the implementation of CSRF (Cross-Site Request Forgery) protection for the Abstrauth OAuth2 authorization server.

## Overview

Abstrauth implements CSRF protection using:
- **Backend**: Custom JAX-RS filters (`ApiCsrfFilter` + `ApiCsrfTokenGenerator`)
- **Frontend**: Angular's built-in XSRF protection
- **Pattern**: Signed Double Submit Cookie with HMAC
- **Integration**: Seamless - uses Angular's default cookie/header names

## Why Custom Implementation?

**The `quarkus-rest-csrf` extension was NOT used** because it has a fundamental limitation:

### The Problem with quarkus-rest-csrf

1. **Global Application**: The extension applies CSRF verification to **ALL** POST/PUT/DELETE/PATCH requests across the entire application
2. **No Path Exclusion**: There is no configuration option to exclude specific paths from CSRF verification
3. **Conflicts with OAuth2**: OAuth2 login endpoints (`/oauth2/authorize/authenticate`) already have their own CSRF protection via the `request_id` parameter
4. **Blocks Legitimate Requests**: Enabling `quarkus-rest-csrf` blocks OAuth2 login requests because:
   - Login happens **before** the user has an OIDC session
   - HMAC-signed tokens require a session ID
   - The filter rejects requests without valid HMAC signatures

### Our Solution

We implemented **custom JAX-RS filters** that:
- Only apply to `/api/*` endpoints (where we need CSRF protection)
- Skip `/oauth2/*` endpoints (which use `request_id` for CSRF protection)
- Use HMAC signing to bind tokens to user sessions
- Integrate seamlessly with Angular's XSRF mechanism
- Give us full control over which endpoints require CSRF tokens

## Current Security Posture

The application already has:
- **CSP (Content Security Policy)** enabled - protects against XSS, clickjacking, and code injection
- **OIDC session management** via encrypted cookies (stateless, using `quarkus-oidc`)
- **HSTS** enabled in production
- **Rate limiting** on OAuth endpoints

## CSRF Protection Strategy

### Architecture Overview

The application will use the **Double Submit Cookie Pattern with HMAC signing** (recommended by OWASP), leveraging built-in framework capabilities:

1. **Quarkus Backend**: Use `quarkus-rest-csrf` extension for token generation and verification
2. **Angular Frontend**: Use built-in `HttpClient` XSRF/CSRF protection
3. **CORS**: Enable and configure properly to work with CSRF protection

### Why This Approach?

- **Stateless**: No server-side token storage required (fits with current architecture)
- **HMAC-signed tokens**: Prevents token forgery by binding tokens to the user's session
- **Framework-native**: Both Quarkus and Angular have built-in CSRF support
- **Defense in depth**: Works alongside existing CSP and session management

## Implementation Steps

### 1. Create Custom CSRF Filters

**ApiCsrfFilter.java** - Validates CSRF tokens on mutating requests to `/api/*`:

```java
@Provider
@Priority(Priorities.AUTHENTICATION + 1)
public class ApiCsrfFilter implements ContainerRequestFilter {
    // Validates CSRF tokens on POST/PUT/DELETE/PATCH to /api/*
    // Verifies HMAC signature binds token to user session
    // Uses constant-time comparison to prevent timing attacks
}
```

**ApiCsrfTokenGenerator.java** - Generates CSRF tokens on GET requests to `/api/*`:

```java
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class ApiCsrfTokenGenerator implements ContainerResponseFilter {
    // Generates HMAC-signed tokens on GET requests to /api/*
    // Token format: base64(hmac).base64(random)
    // Sets XSRF-TOKEN cookie for Angular to read
}
```

### 2. Configure CSRF Protection

Add to `src/main/resources/application.properties`:

```properties
# HMAC signature key for CSRF tokens (min 32 characters)
# Generate with: openssl rand -base64 64
csrf.token.signature.key=${CSRF_TOKEN_SIGNATURE_KEY:dev-csrf-key-CHANGE-IN-PRODUCTION-must-be-at-least-32-characters-long-for-security}
```

**Security Features:**
- **HMAC Signing**: Tokens are cryptographically bound to the user's session (principal name)
- **Token Format**: `base64(hmac).base64(random)` - 128-bit random value with HMAC signature
- **Cookie Name**: `XSRF-TOKEN` (matches Angular default)
- **Header Name**: `X-XSRF-TOKEN` (matches Angular default)
- **HttpOnly**: `false` (so Angular can read the cookie)
- **SameSite**: `Strict`
- **Max-Age**: Reuses `abstrauth.session.timeout.seconds` (900 seconds)

### 3. Configure CORS

Add to `src/main/resources/application.properties`:

```properties
# ============================================================================
# CORS Configuration
# ============================================================================

# Enable CORS filter
quarkus.http.cors.enabled=true

# Allowed origins - restrict to your domains
%dev.quarkus.http.cors.origins=http://localhost:4200,http://localhost:8080
%test.quarkus.http.cors.origins=http://localhost:8080
%e2e.quarkus.http.cors.origins=http://localhost:8080
%prod.quarkus.http.cors.origins=https://abstrauth.abstratium.dev,https://auth.abstratium.dev

# Allowed methods
quarkus.http.cors.methods=GET,POST,PUT,DELETE,PATCH,OPTIONS

# Allowed headers - include CSRF header
quarkus.http.cors.headers=Content-Type,Authorization,X-XSRF-TOKEN

# Expose headers
quarkus.http.cors.exposed-headers=Content-Disposition

# Allow credentials (required for cookies)
quarkus.http.cors.access-control-allow-credentials=true

# Preflight cache duration
quarkus.http.cors.access-control-max-age=24H
```

**CORS Security Notes:**

- **Never use `origins=*` in production** - always specify exact origins
- **`access-control-allow-credentials=true`** is required for cookie-based CSRF protection
- The CORS filter can also provide CSRF protection via Origin header verification (defense in depth)

### 4. Configure Angular CSRF Protection

Angular's `HttpClient` has built-in CSRF protection that works automatically. Update `src/main/webui/src/app/app.config.ts`:

```typescript
import { HttpClient, provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { AuthService } from './auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      })
    ),
    provideAppInitializer(() => {
      const authService = inject(AuthService);
      return firstValueFrom(authService.initialize());
    }),
  ]
};
```

**How Angular CSRF Protection Works:**

1. Angular reads the `XSRF-TOKEN` cookie (set by Quarkus)
2. On mutating requests (POST, PUT, DELETE, PATCH), Angular automatically adds the `X-XSRF-TOKEN` header
3. GET and HEAD requests are not protected (they should not change state)
4. The token is only sent to same-origin or relative URLs

### 5. Environment Variables for Production

Add to your production environment configuration:

```bash
# CSRF token signature key (minimum 32 characters)
# Generate with: openssl rand -base64 64
export CSRF_TOKEN_SIGNATURE_KEY="your-secure-random-key-here"
```

## How HMAC CSRF Tokens Work

### Token Generation (Quarkus)

When Quarkus generates a CSRF token with HMAC signing:

1. **Extract session ID** from the OIDC session cookie (encrypted, server-side only)
2. **Generate random value** (cryptographically secure, 16 bytes)
3. **Create HMAC message**: `sessionID.length + "!" + sessionID + "!" + randomValue.length + "!" + randomValue`
4. **Generate HMAC**: `HMAC-SHA256(secret, message)`
5. **Create token**: `hmac.hex + "." + randomValue.hex`
6. **Set cookie**: `XSRF-TOKEN=token; Secure; SameSite=Strict`

### Token Verification (Quarkus)

When Quarkus verifies a CSRF token:

1. **Extract token** from `X-XSRF-TOKEN` header (sent by Angular)
2. **Split token**: `hmacFromRequest` and `randomValue`
3. **Get current session ID** from OIDC session cookie
4. **Recreate HMAC message** using session ID and random value from request
5. **Generate expected HMAC**: `HMAC-SHA256(secret, message)`
6. **Compare HMACs** using constant-time comparison
7. **Reject if mismatch** (403 Forbidden)

### Security Properties

- **Session binding**: Token is cryptographically bound to the user's session
- **Unpredictable**: Random value prevents token prediction
- **Tamper-proof**: HMAC signature prevents token forgery
- **Stateless**: No server-side token storage required
- **Time-limited**: Token expires with the cookie (2 hours by default)

## Protected Endpoints

CSRF protection applies to:

- **POST** requests (create operations)
- **PUT** requests (update operations)
- **PATCH** requests (partial update operations)
- **DELETE** requests (delete operations)

CSRF protection does NOT apply to:

- **GET** requests (read-only, protected by same-origin policy)
- **HEAD** requests (metadata only)
- **OPTIONS** requests (CORS preflight)

## Testing CSRF Protection

### Unit Tests

Unit tests verify CSRF configuration but do not test full CSRF functionality because:
- Tests use JWT tokens directly (via `.auth().oauth2()`) without establishing OIDC sessions
- HMAC-signed CSRF tokens require session IDs from OIDC cookies
- CSRF is disabled in the test profile to avoid conflicts with JWT-based testing

The `CsrfProtectionTest` class verifies:
1. **Configuration**: CSRF cookie and header names match Angular defaults
2. **Public endpoints**: Well-known endpoints remain accessible without CSRF tokens

### E2E Tests

Full CSRF protection testing is performed in E2E tests where:
1. User logs in via OIDC (establishes session with encrypted cookie)
2. User loads a page (receives CSRF token cookie)
3. User submits a form (Angular sends token in header)
4. Server validates HMAC-signed token against session
5. Request succeeds or fails appropriately

E2E tests verify:
- **Token generation**: GET request creates CSRF cookie
- **Token validation**: POST/PUT/DELETE with valid token succeeds
- **Missing token**: POST/PUT/DELETE without token fails (400)
- **Invalid token**: POST/PUT/DELETE with wrong token fails (400)
- **Token binding**: Token is cryptographically bound to session

### Manual Testing

1. Open browser DevTools → Network tab
2. Make a GET request to any API endpoint
3. Check Response Headers for `Set-Cookie: XSRF-TOKEN=...`
4. Make a POST/PUT/DELETE request
5. Check Request Headers for `X-XSRF-TOKEN: ...`
6. Verify the token value matches the cookie value
7. Try modifying the token - request should fail with 400

## Security Considerations

### Defense in Depth

CSRF protection works best as part of a layered security approach:

1. **CSRF tokens** (primary defense)
2. **SameSite cookies** (browser-level protection) - already configured via OIDC
3. **Origin/Referer validation** (via CORS filter)
4. **CSP** (prevents XSS attacks that could steal tokens) - already enabled
5. **HTTPS** (prevents token interception) - enforced in production

### SameSite Cookie Attribute

The OIDC session cookie should use `SameSite=Strict` or `SameSite=Lax`:

- Already configured: `quarkus.oidc.bff.authentication.cookie-same-site=strict`
- This provides additional CSRF protection at the browser level
- Modern browsers (Chrome, Firefox, Edge, Safari) support this

### HTTPS Requirement

- CSRF tokens should only be transmitted over HTTPS in production
- Already configured: `quarkus.rest-csrf.cookie-force-secure=true` in prod
- HSTS is already enabled in production

### Token Storage

- **Never store CSRF tokens in localStorage** (vulnerable to XSS)
- Use cookies (HttpOnly=false so Angular can read, but Secure=true in prod)
- Angular automatically handles token extraction and header injection

## Common Pitfalls to Avoid

### 1. Don't Protect GET Requests

GET requests should be idempotent and not change state. CSRF protection on GET requests:
- Breaks browser back button
- Breaks bookmarks
- Breaks link sharing
- Is unnecessary (same-origin policy protects GET responses)

### 2. Don't Use HttpOnly for CSRF Cookies

The CSRF token cookie must have `HttpOnly=false` so JavaScript can read it. This is safe because:
- The token is not sensitive (it's a random value)
- The HMAC signature prevents forgery
- The token is bound to the session via HMAC

### 3. Don't Hardcode the Signature Key

- Use environment variables in production
- Generate a strong random key (minimum 32 characters)
- Rotate keys periodically (requires invalidating all tokens)

### 4. Don't Allow All Origins in CORS

- Always specify exact origins in production
- Use regex patterns carefully (escape special characters)
- Test CORS configuration thoroughly

### 5. Don't Disable CSRF for Convenience

- If a specific endpoint doesn't need CSRF protection, document why
- Consider if the endpoint truly doesn't change state
- Use `@PermitAll` or similar annotations carefully

## Monitoring and Logging

Add logging for CSRF failures:

```java
// Quarkus automatically logs CSRF failures at WARN level
// Monitor these logs for potential attacks:
// - High frequency of CSRF failures from same IP
// - CSRF failures after successful login
// - CSRF failures with valid session cookies
```

Consider adding metrics:

- Count of CSRF token generations
- Count of CSRF validation failures
- Count of CSRF validation successes

## Migration Path

1. **Phase 1**: Add dependencies and configuration (no impact)
2. **Phase 2**: Enable CSRF in dev/test environments
3. **Phase 3**: Test thoroughly with integration tests
4. **Phase 4**: Deploy to staging with monitoring
5. **Phase 5**: Deploy to production with gradual rollout

## References

- [Quarkus CSRF Prevention Guide](https://quarkus.io/guides/security-csrf-prevention)
- [Quarkus CORS Guide](https://quarkus.io/guides/security-cors)
- [Angular XSRF/CSRF Security](https://angular.dev/best-practices/security#httpclient-xsrf-csrf-security)
- [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [RFC 6265bis - SameSite Cookies](https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-02)

## Summary

This implementation provides robust CSRF protection by:

1. Using framework-native capabilities (Quarkus + Angular)
2. Implementing HMAC-signed tokens bound to user sessions
3. Maintaining stateless architecture (no server-side token storage)
4. Leveraging defense in depth (CSRF + SameSite + CORS + CSP)
5. Following OWASP best practices

The implementation is production-ready, testable, and compatible with the existing OAuth2 authorization server architecture.
