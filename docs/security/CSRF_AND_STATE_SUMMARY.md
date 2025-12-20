# OAuth2 State Parameter and CSRF Protection - Summary

## Executive Summary

✅ **Your application is now secure against CSRF attacks** after the fixes applied in this session.

## What Was Fixed

### Critical Security Vulnerability (FIXED)

**Issue**: The Angular SPA was not validating the `state` parameter in OAuth callbacks, leaving it vulnerable to CSRF attacks.

**Impact**: An attacker could trick users into completing an OAuth flow initiated by the attacker, potentially:
- Linking victim's session to attacker's account
- Exposing victim's data to the attacker
- Session hijacking

**Fix Applied**:
1. ✅ Added `state` parameter to authorization request (`authorize.component.ts`)
2. ✅ Added state validation in callback (`auth-callback.component.ts`)
3. ✅ Updated documentation (`SECURITY.md`, `FLOWS.md`)

## Understanding OAuth2 State Parameter

### Primary Purpose: CSRF Protection (Security)

The `state` parameter is **primarily a security mechanism** to prevent Cross-Site Request Forgery attacks in OAuth2 flows.

**How it works:**
```
1. Client generates random state: "abc123xyz"
2. Client stores in sessionStorage
3. Client sends state to authorization server
4. Authorization server returns state unchanged in callback
5. Client validates: received state === stored state
6. If mismatch → REJECT (CSRF attack detected)
```

### Secondary Purpose: Application State (Convenience)

The state parameter can also encode application state (e.g., which page the user was on), but this is **secondary** to its security purpose.

## CSRF Attack Scenario (Without State Validation)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Attacker initiates OAuth flow with their own account     │
│    /oauth2/authorize?client_id=...&state=ATTACKER_STATE     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. Attacker captures authorization code from redirect       │
│    https://attacker.com/callback?code=ATTACKER_CODE         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Attacker tricks victim to visit malicious page           │
│    <img src="https://victim-app.com/callback?code=CODE">    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Victim's browser automatically sends request             │
│    Victim's session gets linked to attacker's account       │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. Victim unknowingly uses attacker's account               │
│    All victim's data goes to attacker's account             │
└─────────────────────────────────────────────────────────────┘
```

## Protection With State Validation (Current Implementation)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Legitimate user initiates OAuth flow                     │
│    Client generates state="xyz123" and stores it            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. Authorization server returns callback                    │
│    /callback?code=CODE&state=xyz123                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Client validates state                                   │
│    received "xyz123" === stored "xyz123" ✅                 │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. If attacker tries to inject their code                   │
│    received "ATTACKER_STATE" !== stored "xyz123" ❌         │
│    → REJECT: "Possible CSRF attack detected"                │
└─────────────────────────────────────────────────────────────┘
```

## Implementation Details

### Client-Side (Angular SPA)

#### 1. Authorization Initiation (`authorize.component.ts`)

```typescript
async authorize() {
    // Generate cryptographically random state for CSRF protection
    const state = this.generateRandomString(32);
    
    // Store for later validation in callback
    sessionStorage.setItem('state', state);
    
    const params = new URLSearchParams({
        response_type: 'code',
        client_id: CLIENT_ID,
        redirect_uri: '...',
        scope: 'openid profile email',
        state: state,  // ← Include state parameter
        code_challenge: codeChallenge,
        code_challenge_method: 'S256'
    });
    
    window.location.href = `/oauth2/authorize?${params}`;
}
```

#### 2. Callback Validation (`auth-callback.component.ts`)

```typescript
exchangeToken() {
    // CRITICAL: Validate state parameter to prevent CSRF attacks
    const receivedState = this.route.snapshot.queryParamMap.get('state');
    const storedState = sessionStorage.getItem('state');
    
    if (!receivedState || !storedState || receivedState !== storedState) {
        this.error = 'Security Error: Invalid state parameter. Possible CSRF attack detected.';
        console.error('CSRF Protection: State mismatch');
        // Clear stored values
        sessionStorage.removeItem('state');
        sessionStorage.removeItem('code_verifier');
        return;  // ← REJECT the callback
    }
    
    // Clear state after successful validation
    sessionStorage.removeItem('state');
    
    // Continue with token exchange...
}
```

### Server-Side (Java/Quarkus)

#### 1. Native Login Flow

**State Storage:**
- State is stored in `AuthorizationRequest` entity when client initiates OAuth flow
- State is validated when generating authorization code
- State is returned to client in redirect

**Code Reference:** `AuthorizationResource.java`

#### 2. Federated Login Flow (Google)

**State as Request ID:**
- State parameter contains the `AuthorizationRequest.id`
- Google callback validates state by looking up the authorization request
- Invalid state returns 400 Bad Request

**Code Reference:** `GoogleCallbackResource.java`

```java
// Find the original authorization request using the state parameter
Optional<AuthorizationRequest> requestOpt = 
    authorizationService.findAuthorizationRequest(state);
    
if (requestOpt.isEmpty() || !"pending".equals(requestOpt.get().getStatus())) {
    return Response.status(Response.Status.BAD_REQUEST)
            .entity("Invalid or expired authorization request")
            .build();
}
```

## Additional CSRF Protections

Your application has **defense in depth** with multiple layers of CSRF protection:

### 1. State Parameter Validation ✅
- Primary CSRF protection
- Validates callback matches original request

### 2. PKCE (Proof Key for Code Exchange) ✅
- Prevents authorization code interception
- Code verifier stored in sessionStorage
- Server validates code_verifier matches code_challenge

### 3. Short-Lived Authorization Codes ✅
- Codes expire after 10 minutes
- Single-use only (marked as used after exchange)
- Reduces window for CSRF attacks

### 4. Redirect URI Validation ✅
- Server validates redirect_uri matches registered client
- Prevents code leakage to attacker's domain

### 5. SameSite Cookies ✅
- Refresh tokens use `SameSite=Lax` attribute
- Prevents CSRF on token refresh endpoint

## Security Checklist

### ✅ Implemented

- [x] State parameter generation (cryptographically random, 32 characters)
- [x] State parameter storage (sessionStorage)
- [x] State parameter sent to authorization server
- [x] State parameter validation in callback
- [x] State parameter cleared after use
- [x] PKCE implementation
- [x] Authorization code expiration (10 minutes)
- [x] Single-use authorization codes
- [x] Redirect URI validation
- [x] SameSite cookies for refresh tokens

### 📋 Recommended for Production

- [x] HTTPS only (configure reverse proxy) - HSTS automatically enabled in prod profile
- [x] Content Security Policy (CSP) headers - Implemented
- [x] Rate limiting on OAuth endpoints - Implemented
- [ ] Logging and monitoring for security events
- [ ] Regular security audits

## Testing CSRF Protection

### Manual Test

1. **Initiate OAuth flow** and capture the state parameter
2. **Modify the state** in the callback URL
3. **Verify** the application rejects the callback with error message

**Expected Result:**
```
Security Error: Invalid state parameter. Possible CSRF attack detected.
```

### Automated Test

Add E2E test to verify state validation:

```typescript
test('rejects callback with invalid state', async ({ page }) => {
  // Initiate OAuth flow
  await page.goto('/authorize');
  
  // Manually navigate to callback with wrong state
  await page.goto('/auth-callback?code=test&state=WRONG_STATE');
  
  // Should show error
  await expect(page.locator('.error-box'))
    .toContainText('Invalid state parameter');
});
```

## References

### RFCs
- [RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749) - OAuth 2.0 Authorization Framework
- [RFC 6819](https://datatracker.ietf.org/doc/html/rfc6819) - OAuth 2.0 Threat Model and Security Considerations
- [RFC 9700](https://datatracker.ietf.org/doc/html/rfc9700) - OAuth 2.0 Security Best Current Practice

### Project Documentation
- `SECURITY.md` - Comprehensive security implementation guide
- `FLOWS.md` - OAuth2 flow diagrams with security annotations
- `FEDERATED_LOGIN.md` - Federated login security considerations

## Conclusion

Your OAuth2 implementation now has **robust CSRF protection** through:

1. ✅ **State parameter validation** - Primary defense against CSRF
2. ✅ **PKCE** - Defense against authorization code interception
3. ✅ **Short-lived codes** - Reduces attack window
4. ✅ **Redirect URI validation** - Prevents code leakage
5. ✅ **SameSite cookies** - Additional CSRF protection

The state parameter serves **primarily as a security mechanism** (CSRF protection), with application state preservation being a secondary benefit.

**Key Takeaway:** Always validate the state parameter in OAuth callbacks. This is not optional - it's a critical security requirement.
