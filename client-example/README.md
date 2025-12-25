# Abstrauth OAuth 2.0 Client Example

This is a complete example implementation of an OAuth 2.0 client application that integrates with the Abstrauth authorization server using the **Authorization Code Flow with PKCE** (Proof Key for Code Exchange).

## Overview

This example demonstrates:

- ✅ OAuth 2.0 Authorization Code Flow with PKCE
- ✅ CSRF protection using state parameter
- ✅ Browser-side PKCE parameter generation
- ✅ Stateless server architecture
- ✅ HTTP-only cookie token storage
- ✅ JWT token parsing
- ✅ User information display

## Architecture

This is a **stateless server** architecture:
- Browser generates PKCE parameters (`code_verifier`, `code_challenge`) and `state`
- Browser stores `code_verifier` and `state` in `sessionStorage` (short-lived)
- Server exchanges authorization code for token
- Server stores access token in HTTP-only cookie (XSS-proof)
- Server has NO sessions, NO in-memory storage

```
┌─────────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   Browser (SPA)     │ ◄─────► │  Stateless Server│ ◄─────► │ Abstrauth       │
│                     │         │  localhost:3333  │         │ Auth Server     │
│ - PKCE generation   │         │  - Token exchange│         │ localhost:8080  │
│ - sessionStorage    │         │  - HTTP-only     │         │                 │
│ - OAuth flow        │         │    cookies       │         │                 │
└─────────────────────┘         └──────────────────┘         └─────────────────┘
```

## Prerequisites

- Node.js 16+ installed
- Abstrauth authorization server running on `http://localhost:8080`
- OAuth client registered in Abstrauth with:
  - **Client ID**: `anapp-acomp`
  - **Redirect URI**: `http://localhost:3333`
  - **Scopes**: `openid profile email`

## Installation

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start the server:
   ```bash
   npm start
   ```

3. Open your browser and navigate to:
   ```
   http://localhost:3333
   ```

## Configuration

The server can be configured using environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3333` | Port to run the server on |
| `CLIENT_ID` | `anapp-acomp` | OAuth client ID |
| `REDIRECT_URI` | `http://localhost:3333` | OAuth redirect URI |
| `AUTHORIZATION_ENDPOINT` | `http://localhost:8080/oauth2/authorize` | Authorization endpoint |
| `TOKEN_ENDPOINT` | `http://localhost:8080/oauth2/token` | Token endpoint |
| `SCOPE` | `openid profile email` | OAuth scopes to request |

## How It Works

### 1. User Visits Home Page

When the user visits `http://localhost:3333`:

1. Browser loads the SPA (`index.html` + `app.js`)
2. Browser attempts to fetch user info from `/api/user`
3. If not authenticated (no HTTP-only cookie), browser initiates OAuth flow

### 2. Browser Initiates OAuth Flow

The browser JavaScript (`app.js`):

1. Uses hard-coded OAuth configuration (client_id, redirect_uri, etc.)
2. Generates a cryptographically secure `code_verifier` (32 bytes, base64url-encoded)
3. Creates a `code_challenge` by hashing the `code_verifier` with SHA-256
4. Generates a random `state` parameter for CSRF protection (16 bytes)
5. **Stores `code_verifier` and `state` in `sessionStorage`** (short-lived, browser-side)
6. Redirects the user to the authorization endpoint (`/oauth2/authorize`) with:
   - `response_type=code`
   - `client_id=anapp-acomp`
   - `redirect_uri=http://localhost:3333`
   - `scope=openid profile email`
   - `state=<random_string>`
   - `code_challenge=<SHA256_hash>`
   - `code_challenge_method=S256`

**IMPORTANT**: PKCE parameters are generated and stored **in the browser** (not on the server). This is the correct implementation for SPAs according to OAuth 2.0 best practices.

### 3. User Authenticates

The user is redirected to the Abstrauth authorization server where they:

1. Sign in with their credentials (or use federated login like Google)
2. Review the requested permissions (consent screen)
3. Approve or deny the authorization request

### 4. Authorization Code Returned

If the user approves, Abstrauth redirects back to the callback URL with:
- `code=<authorization_code>` - Single-use authorization code
- `state=<same_random_string>` - For CSRF validation

### 5. Browser Handles Callback

The browser JavaScript (`app.js`):

1. Detects the callback URL with `code` and `state` parameters
2. **Validates the `state` parameter** against the value in `sessionStorage` (CSRF protection)
3. Retrieves the `code_verifier` from `sessionStorage`
4. Sends a POST request to `/api/token` with:
   - `code=<authorization_code>`
   - `code_verifier=<original_code_verifier>`

### 6. Server Exchanges Code for Token

The stateless server (`server.js`):

1. Receives the authorization code and `code_verifier` from the browser
2. Exchanges the authorization code for tokens by calling the token endpoint (`/oauth2/token`) with:
   - `grant_type=authorization_code`
   - `code=<authorization_code>`
   - `redirect_uri=http://localhost:3333`
   - `client_id=anapp-acomp`
   - `code_verifier=<code_verifier>` (PKCE verification)
3. **Stores the access token in an HTTP-only cookie** (not accessible to JavaScript)
4. Returns success to the browser (no token in response body)

### 7. Browser Clears PKCE Parameters

After successful token exchange:

1. Browser clears `code_verifier` and `state` from `sessionStorage`
2. Browser redirects to `/` (clean URL)
3. Browser fetches user info from `/api/user`

### 8. User Information Displayed

The server (`/api/user` endpoint):

1. Reads the access token from the HTTP-only cookie
2. Parses the JWT to extract user claims (sub, email, name, groups)
3. Returns user information to the browser
4. Browser displays the user information on the dashboard

## Security Features

### PKCE (Proof Key for Code Exchange)

PKCE prevents authorization code interception attacks:

1. **Code Verifier**: Random string (43-128 characters) generated by the client
2. **Code Challenge**: SHA-256 hash of the code verifier, sent to authorization server
3. **Verification**: Authorization server verifies that SHA-256(code_verifier) matches the stored code_challenge

This ensures that even if an attacker intercepts the authorization code, they cannot exchange it for tokens without the original code_verifier.

### State Parameter (CSRF Protection)

The `state` parameter prevents Cross-Site Request Forgery attacks:

1. Client generates a random state value
2. Stores it in the session
3. Includes it in the authorization request
4. Validates that the returned state matches the stored value

If an attacker tricks a user into visiting a malicious callback URL, the state validation will fail.

### PKCE Parameter Storage (Browser)

- `code_verifier` and `state` are stored in browser `sessionStorage`
- These are **short-lived** (seconds to minutes) and cleared after token exchange
- This is the **correct and required** approach for SPAs per OAuth 2.0 best practices
- OWASP concerns about `sessionStorage` apply to **long-lived tokens**, not PKCE parameters

### Token Storage (Server)

- Access token is stored in an **HTTP-only cookie** (not accessible to JavaScript)
- Protects against XSS attacks (even if XSS vulnerability exists, token cannot be stolen)
- Cookie has `sameSite=lax` for CSRF protection
- Cookie has `secure=true` in production (HTTPS only)
- Server is **completely stateless** - no sessions, no in-memory storage

## Why This Architecture?

This example uses a **hybrid architecture**: browser-side OAuth flow with server-side token exchange and HTTP-only cookie storage. This combines the benefits of both approaches.

### Security Rationale

#### ✅ PKCE Parameters in Browser Storage (Correct)

**Why `code_verifier` and `state` are in `sessionStorage`:**

1. **Required by OAuth 2.0 for SPAs**: PKCE was designed specifically for public clients (SPAs, mobile apps) that cannot securely store secrets on a backend
2. **Short-lived**: These values exist for seconds to minutes, not hours or days
3. **Cleared after use**: Immediately removed from `sessionStorage` after token exchange
4. **Not the same as tokens**: OWASP warnings about `localStorage`/`sessionStorage` apply to **long-lived access tokens and refresh tokens**, not temporary PKCE parameters

**From your research:**
> "For SPAs and mobile apps, storing code_verifier and state in browser storage is not just acceptable—it's required by design. The critical distinction is that these are short-lived flow parameters, not the long-lived tokens that should never be in localStorage."

#### ✅ Access Token in HTTP-Only Cookie (Secure)

**Why the access token is in an HTTP-only cookie:**

1. **XSS Protection**: Even if XSS vulnerability exists, JavaScript cannot access HTTP-only cookies
2. **Equivalent to session-based**: Cookie gives access to server, which uses token - same security as session ID
3. **Stateless server**: No server-side sessions needed, server remains stateless
4. **CSRF Protection**: `sameSite=lax` prevents CSRF attacks
5. **HTTPS in production**: `secure=true` ensures cookie only sent over HTTPS

#### ✅ PKCE Security

**Why PKCE parameters CAN be in browser storage:**

1. **Designed for public clients**: PKCE (RFC 7636) was specifically created for SPAs and mobile apps that cannot keep secrets
2. **Short-lived**: `code_verifier` only exists for the duration of the OAuth flow (seconds to minutes)
3. **Single-use**: Authorization code can only be exchanged once
4. **Server validates**: The authorization server performs constant-time comparison of `code_challenge`
5. **Cleared immediately**: Browser clears `code_verifier` from `sessionStorage` after token exchange

**What PKCE protects against:**
- Authorization code interception attacks
- Attacker cannot exchange intercepted code without the original `code_verifier`
- Even if attacker sees `code_challenge`, they cannot reverse SHA-256 to get `code_verifier`

#### ✅ CSRF Protection

**State Parameter Security:**

The `state` parameter prevents Cross-Site Request Forgery attacks.

**Browser-side state storage (Current):**
- State stored in `sessionStorage` during OAuth flow
- Browser validates state matches when receiving callback
- State is short-lived (seconds to minutes)
- Cleared immediately after validation

**Why this is secure:**
1. **Short-lived**: State only exists during the OAuth flow
2. **Same-origin**: `sessionStorage` is isolated per origin
3. **Validated immediately**: State is checked and cleared in callback
4. **XSS risk is minimal**: Even if XSS exists, attacker would need to intercept the exact moment of OAuth callback
5. **Additional protection**: HTTP-only cookie with `sameSite=lax` provides defense in depth

#### ✅ Token Storage Compliance

**OAuth 2.0 and Web Security Best Practices:**

1. ✅ **Access tokens NOT in `localStorage` or `sessionStorage`** (OWASP recommendation)
2. ✅ **HTTP-only cookies for tokens** (immune to JavaScript/XSS)
3. ✅ **PKCE parameters in `sessionStorage`** (short-lived, required for SPAs)
4. ✅ **Stateless server** (no sessions, scalable)
5. ✅ **RFC 8725**: Server validates issuer, subject, and audience claims in JWTs

**This Implementation:**
```javascript
// ✅ SECURE - Access token in HTTP-only cookie
res.cookie('access_token', tokens.access_token, {
    httpOnly: true,  // ← Not accessible to JavaScript
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    maxAge: tokens.expires_in * 1000
});

// ✅ SECURE - PKCE parameters in sessionStorage (short-lived)
sessionStorage.setItem('code_verifier', codeVerifier);
sessionStorage.setItem('state', state);
// Cleared immediately after token exchange
```

**What NOT to do:**
```javascript
// ❌ INSECURE - Long-lived tokens in browser storage
localStorage.setItem('access_token', tokens.access_token);
localStorage.setItem('refresh_token', tokens.refresh_token);
// Any XSS vulnerability can steal these tokens
```

#### ✅ Authorization Code Replay Detection

From Abstrauth's security audit:

> **CRITICAL-2: Authorization Code Replay Attack Detection**
> RFC 6749 requires: "If an authorization code is used more than once, the authorization server MUST deny the request and SHOULD revoke all tokens"

**How this architecture handles it:**
- Authorization server (Abstrauth) enforces single-use authorization codes
- Authorization server revokes all tokens if code is replayed
- This is enforced server-side (Abstrauth), not by the client
- Client architecture (stateless vs stateful) doesn't affect this protection

#### ✅ Defense in Depth

**Security Layers:**

| Security Aspect | This Architecture | Pure Browser (localStorage) |
|----------------|-------------------|--------------------------|
| XSS Protection for Tokens | ✅ HTTP-only cookies (JS cannot access) | ❌ Vulnerable (JS can access) |
| PKCE Parameters | ✅ sessionStorage (short-lived, required) | ✅ Same |
| CSRF Protection | ✅ State + sameSite cookie | ✅ State parameter |
| Token Storage | ✅ HTTP-only cookie | ❌ localStorage risk |
| Replay Detection | ✅ Server enforces | ✅ Server enforces |
| DevTools Inspection | ⚠️ Visible in DevTools (but not to JS) | ❌ Visible in DevTools AND to JS |
| Malicious Extensions | ✅ Protected (cannot use document.cookie) | ❌ Can steal via JS APIs |
| Server Stateless | ✅ Fully stateless | ✅ Fully stateless |

### Comparison with Other Architectures

#### Pure Browser (Token in Memory)

**Pros:**
- No backend needed for token storage
- Fully stateless

**Cons:**
- Token lost on page refresh (poor UX)
- Must re-authenticate frequently
- Cannot use refresh tokens securely

#### Pure Browser (Token in localStorage)

**Pros:**
- Survives page refresh
- No backend needed

**Cons:**
- ❌ **Violates OWASP recommendations**
- ❌ **XSS can steal tokens**
- ❌ **Malicious extensions can access**
- Not recommended for production

#### This Architecture (HTTP-only Cookie)

**Pros:**
- ✅ XSS-proof token storage
- ✅ Survives page refresh
- ✅ Stateless server
- ✅ Can use refresh tokens
- ✅ Follows OAuth 2.0 best practices

**Cons:**
- Requires minimal backend (just token exchange endpoint)

### This Architecture (Recommended)

For **production OAuth clients**, this hybrid architecture is recommended:

```
┌─────────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   Browser (SPA)     │ ◄─────► │  Stateless Server│ ◄─────► │ OAuth Server    │
│                     │         │  (Node/Java/etc) │         │ (Abstrauth)     │
│                     │         │                  │         │                 │
│  - PKCE generation  │         │  - Token exchange│         │  - Issues tokens│
│  - OAuth flow       │         │  - HTTP-only     │         │  - Validates    │
│  - sessionStorage   │         │    cookies       │         │  - PKCE verify  │
│    (PKCE params)    │         │  - NO sessions   │         │                 │
└─────────────────────┘         └──────────────────┘         └─────────────────┘
```

**Benefits:**
- ✅ Access tokens never exposed to JavaScript (HTTP-only cookies)
- ✅ PKCE parameters in browser (required for SPAs, short-lived)
- ✅ Stateless server (scalable, no session management)
- ✅ Compliance with OAuth 2.0 and OWASP best practices
- ✅ Defense in depth against XSS attacks

### Compliance Summary

This hybrid implementation complies with:

- ✅ **RFC 6749** (OAuth 2.0) - Authorization Code Flow
- ✅ **RFC 7636** (PKCE) - PKCE parameters in browser (as designed for public clients)
- ✅ **RFC 6819** (Security Considerations) - CSRF protection via state parameter
- ✅ **RFC 9700** (Security Best Practices) - PKCE enforcement for public clients
- ✅ **RFC 8725** (JWT Best Practices) - Server validates issuer, subject, and audience claims
- ✅ **OWASP** - Access tokens in HTTP-only cookies, NOT in localStorage/sessionStorage
- ✅ **OAuth 2.0 for Browser-Based Apps** - PKCE parameters in sessionStorage (short-lived)

**Key Distinction:**
- ✅ **PKCE parameters** (`code_verifier`, `state`) in `sessionStorage` - **CORRECT** (short-lived, required)
- ✅ **Access tokens** in HTTP-only cookies - **CORRECT** (XSS-proof)
- ❌ **Access tokens** in `localStorage`/`sessionStorage` - **WRONG** (OWASP violation)

## API Endpoints

### Public Endpoints

- `GET /` - Home page (serves SPA)
- `POST /api/token` - Exchange authorization code for access token (sets HTTP-only cookie)

### Protected Endpoints

- `GET /api/user` - Get current user information (requires access_token cookie)
- `POST /api/logout` - Clear authentication cookies

## File Structure

```
client-example/
├── server.js           # Express server with OAuth logic
├── package.json        # Dependencies and scripts
├── public/
│   ├── index.html      # Protected dashboard page
│   └── app.js          # Client-side JavaScript
└── README.md           # This file
```

## Code Examples

### Generating PKCE Parameters

```javascript
function generateRandomString(length) {
  const bytes = crypto.randomBytes(length);
  return base64URLEncode(bytes);
}

function generateCodeChallenge(codeVerifier) {
  const hash = crypto.createHash('sha256').update(codeVerifier).digest();
  return base64URLEncode(hash);
}

const codeVerifier = generateRandomString(32);
const codeChallenge = generateCodeChallenge(codeVerifier);
```

### Building Authorization URL

```javascript
const authUrl = new URL('http://localhost:8080/oauth2/authorize');
authUrl.searchParams.set('response_type', 'code');
authUrl.searchParams.set('client_id', 'anapp-acomp');
authUrl.searchParams.set('redirect_uri', 'http://localhost:3333');
authUrl.searchParams.set('scope', 'openid profile email');
authUrl.searchParams.set('state', state);
authUrl.searchParams.set('code_challenge', codeChallenge);
authUrl.searchParams.set('code_challenge_method', 'S256');
```

### Exchanging Code for Token

```javascript
const tokenParams = new URLSearchParams({
  grant_type: 'authorization_code',
  code: code,
  redirect_uri: 'http://localhost:3333',
  client_id: 'anapp-acomp',
  code_verifier: codeVerifier
});

const response = await fetch('http://localhost:8080/oauth2/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: tokenParams.toString()
});

const tokens = await response.json();
```

## Testing

This example is used in the e2e tests. To run the tests:

```bash
cd ../e2e-tests
npm test tests-nosignup/client-integration.spec.ts
```

## Production Considerations

This is a demonstration application. For production use, consider:

1. **Session Storage**: Use Redis or a database instead of in-memory storage
2. **HTTPS**: Always use HTTPS in production
3. **Token Validation**: Verify JWT signatures using the JWKS endpoint
4. **Refresh Tokens**: Implement token refresh logic
5. **Error Handling**: Add comprehensive error handling and logging
6. **Rate Limiting**: Implement rate limiting on endpoints
7. **Security Headers**: Add security headers (CSP, HSTS, etc.)
8. **Token Revocation**: Implement logout with token revocation
9. **Environment Variables**: Use proper secret management (e.g., HashiCorp Vault)
10. **Monitoring**: Add application monitoring and alerting

## References

- [RFC 6749 - OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 7636 - Proof Key for Code Exchange (PKCE)](https://datatracker.ietf.org/doc/html/rfc7636)
- [RFC 6819 - OAuth 2.0 Threat Model and Security Considerations](https://datatracker.ietf.org/doc/html/rfc6819)
- [RFC 9700 - OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/rfc9700)

## License

Apache License 2.0
