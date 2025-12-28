# Abstrauth OAuth 2.0 Client Example (BFF Pattern)

This is a complete example implementation of an OAuth 2.0 **confidential client** application that integrates with the Abstrauth authorization server using the **Backend For Frontend (BFF)** pattern with **Authorization Code Flow + PKCE**.

## Overview

This example demonstrates the **recommended architecture** for modern web applications:

- ✅ **Backend For Frontend (BFF) Pattern** - Backend handles all OAuth responsibilities
- ✅ **Confidential Client** - Uses `client_secret` for authentication
- ✅ **Authorization Code Flow with PKCE** - Server-side PKCE generation and validation
- ✅ **Encrypted HTTP-only Cookies** - Tokens never exposed to JavaScript
- ✅ **CSRF Protection** - State parameter validated server-side
- ✅ **Zero Token Exposure** - Frontend never sees tokens or OAuth parameters
- ✅ **Compliant with OAuth 2.0 Security Best Practices** - Follows [RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749) and [OAuth 2.0 for Browser-Based Apps](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps)

## Architecture

This implements the **Backend For Frontend (BFF)** pattern:

```
┌─────────────────────┐         ┌──────────────────────────┐         ┌─────────────────┐
│   Browser (SPA)     │ ◄─────► │  BFF (Node.js Backend)   │ ◄─────► │ Abstrauth       │
│                     │         │  localhost:3333          │         │ Auth Server     │
│ - No OAuth params   │         │  - PKCE generation       │         │ localhost:8080  │
│ - No tokens         │         │  - State management      │         │                 │
│ - Just UI           │         │  - Token exchange        │         │                 │
│                     │         │  - Encrypted cookies     │         │                 │
│                     │         │  - client_secret auth    │         │                 │
└─────────────────────┘         └──────────────────────────┘         └─────────────────┘
```

### Key Principles:

1. **Frontend Never Handles OAuth** - No PKCE generation, no state, no tokens in JavaScript
2. **Backend is Confidential Client** - Authenticates with `client_secret`
3. **Encrypted Token Storage** - Tokens encrypted with AES-256-GCM before storing in cookies
4. **Server-Side Sessions** - PKCE parameters stored in server memory during OAuth flow
5. **HTTP-Only Cookies** - Tokens inaccessible to JavaScript (XSS protection)

## Prerequisites

- Node.js 16+ installed
- Abstrauth authorization server running on `http://localhost:8080`
- OAuth client registered in Abstrauth with:
  - **Client ID**: `abstratium-abstrauth`
  - **Client Type**: `confidential`
  - **Client Secret**: Set via environment variable
  - **Redirect URI**: `http://localhost:3333/oauth/callback`
  - **Scopes**: `openid profile email`

## Installation

1. Install dependencies:
   ```bash
   npm install
   ```

2. Set environment variables (or use defaults for development):
   ```bash
   export CLIENT_SECRET="dev-secret-CHANGE-IN-PROD"
   export SESSION_SECRET="dev-session-secret-CHANGE-IN-PROD"
   export COOKIE_SECRET="dev-cookie-secret-CHANGE-IN-PROD"
   ```

3. Start the server:
   ```bash
   npm start
   ```

4. Open your browser and navigate to:
   ```
   http://localhost:3333
   ```

## Configuration

The server can be configured using environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3333` | Port to run the BFF server on |
| `CLIENT_ID` | `abstratium-abstrauth` | OAuth client ID (must be confidential) |
| `CLIENT_SECRET` | `dev-secret-CHANGE-IN-PROD` | OAuth client secret (REQUIRED) |
| `REDIRECT_URI` | `http://localhost:3333/oauth/callback` | OAuth callback URI |
| `AUTHORIZATION_ENDPOINT` | `http://localhost:8080/oauth2/authorize` | Authorization endpoint |
| `TOKEN_ENDPOINT` | `http://localhost:8080/oauth2/token` | Token endpoint |
| `SCOPE` | `openid profile email` | OAuth scopes to request |
| `SESSION_SECRET` | `dev-session-secret-CHANGE-IN-PROD` | Secret for signing session cookies |
| `COOKIE_SECRET` | `dev-cookie-secret-CHANGE-IN-PROD` | Secret for encrypting tokens (min 32 chars) |

**⚠️ IMPORTANT:** In production, use strong secrets and store them securely (e.g., environment variables, secrets manager).

## How It Works (BFF Pattern)

### 1. User Visits Home Page

When the user visits `http://localhost:3333`:

1. Browser loads the SPA (`index.html` + `app.js`)
2. JavaScript attempts to fetch user info from `/api/user`
3. If not authenticated (no cookie), shows "Sign In" button

### 2. User Clicks "Sign In"

The frontend JavaScript simply redirects to `/oauth/login`:

```javascript
function initiateLogin() {
    window.location.href = '/oauth/login';  // That's it!
}
```

**No PKCE generation, no OAuth parameters - the BFF handles everything.**

### 3. BFF Initiates OAuth Flow

The BFF (`/oauth/login` endpoint):

1. Generates cryptographically secure PKCE parameters:
   - `code_verifier`: Random 32-byte string (base64url-encoded)
   - `code_challenge`: SHA-256 hash of `code_verifier`
   - `state`: Random 16-byte string for CSRF protection
2. **Stores `code_verifier` and `state` in server-side session** (in-memory)
3. Builds authorization URL with all OAuth parameters
4. Redirects browser to authorization server

```javascript
app.get('/oauth/login', (req, res) => {
    const codeVerifier = generateRandomString(32);
    const codeChallenge = generateCodeChallenge(codeVerifier);
    const state = generateRandomString(16);
    
    // Store in server session (not browser!)
    req.session.codeVerifier = codeVerifier;
    req.session.state = state;
    
    // Redirect to authorization server
    res.redirect(authUrl.toString());
});
```

### 4. User Authenticates

The user is redirected to the Abstrauth authorization server where they:

1. Sign in with their credentials (or use federated login like Google)
2. Review the requested permissions (consent screen)
3. Approve or deny the authorization request

### 5. Authorization Server Redirects to BFF Callback

If the user approves, Abstrauth redirects to `http://localhost:3333/oauth/callback` with:
- `code=<authorization_code>` - Single-use authorization code
- `state=<same_random_string>` - For CSRF validation

**Important:** The callback goes to the **BFF**, not the frontend.

### 6. BFF Handles Callback and Exchanges Code

The BFF (`/oauth/callback` endpoint):

1. Validates the `state` parameter against server session (CSRF protection)
2. Retrieves `code_verifier` from server session
3. Exchanges authorization code for access token by calling `/oauth2/token` with:
   - `grant_type=authorization_code`
   - `code=<authorization_code>`
   - `redirect_uri=http://localhost:3333/oauth/callback`
   - `client_id=abstratium-abstrauth`
   - **`client_secret=<CLIENT_SECRET>`** (confidential client authentication)
   - `code_verifier=<code_verifier>` (PKCE verification)
4. **Encrypts the access token** using AES-256-GCM
5. **Stores encrypted token in HTTP-only cookie**
6. Clears session data (no longer needed)
7. Redirects browser to `/` (home page)

```javascript
app.get('/oauth/callback', async (req, res) => {
    // Validate state (CSRF protection)
    if (state !== req.session.state) {
        return res.redirect('/?error=invalid_state');
    }
    
    // Exchange code for token with client_secret
    const tokenParams = new URLSearchParams({
        grant_type: 'authorization_code',
        code: code,
        redirect_uri: REDIRECT_URI,
        client_id: CLIENT_ID,
        client_secret: CLIENT_SECRET,  // Confidential client
        code_verifier: req.session.codeVerifier  // PKCE
    });
    
    const tokens = await fetch(TOKEN_ENDPOINT, { ... });
    
    // Encrypt and store in HTTP-only cookie
    const encryptedToken = encryptToken(tokens.access_token);
    res.cookie('access_token', encryptedToken, {
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'Strict'
    });
    
    // Clean up session
    delete req.session.codeVerifier;
    delete req.session.state;
    
    res.redirect('/');
});
```

### 7. Frontend Loads User Info

After redirect to `/`, the frontend:

1. Calls `/api/user` to fetch user information
2. Browser automatically sends the HTTP-only cookie with the request
3. BFF decrypts token from cookie and parses JWT claims
4. BFF returns user info (payload only, no signature)
5. Frontend displays user information

```javascript
app.get('/api/user', (req, res) => {
    const encryptedToken = req.cookies.access_token;
    const accessToken = decryptToken(encryptedToken);
    
    // Parse JWT payload
    const payload = JSON.parse(Buffer.from(tokenParts[1], 'base64').toString());
    
    res.json({
        user: {
            sub: payload.sub,
            email: payload.email,
            name: payload.name,
            groups: payload.groups
        }
    });
});
```

## Security Features

### 1. Backend For Frontend (BFF) Pattern

**Why BFF?**
- Tokens never exposed to browser (XSS protection)
- Confidential client can use `client_secret`
- PKCE parameters never in browser storage
- Compliant with OAuth 2.0 Security Best Practices

**What the Frontend Never Sees:**
- ❌ PKCE parameters (`code_verifier`, `code_challenge`)
- ❌ State parameter
- ❌ Authorization code
- ❌ Access tokens
- ❌ Client secret

### 2. Confidential Client Authentication

This is a **confidential client** that authenticates with `client_secret`:

```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=AUTH_CODE&
client_id=abstratium-abstrauth&
client_secret=SECRET&          ← Confidential client authentication
code_verifier=VERIFIER
```

**Why Confidential?**
- More secure than public clients
- Required by Abstrauth for BFF pattern
- Prevents unauthorized token requests

### 3. PKCE (Proof Key for Code Exchange)

PKCE prevents authorization code interception attacks:

1. **BFF generates** `code_verifier` (random 32-byte string)
2. **BFF creates** `code_challenge` = SHA-256(`code_verifier`)
3. **BFF sends** `code_challenge` to authorization server
4. **BFF stores** `code_verifier` in server session
5. **Authorization server** stores `code_challenge` with authorization code
6. **BFF sends** `code_verifier` when exchanging code for token
7. **Authorization server verifies** SHA-256(`code_verifier`) == stored `code_challenge`

Even if an attacker intercepts the authorization code, they cannot exchange it without the `code_verifier`.

### 4. State Parameter (CSRF Protection)

The `state` parameter prevents Cross-Site Request Forgery attacks:

1. BFF generates random `state` value
2. BFF stores it in server session
3. BFF includes it in authorization request
4. Authorization server returns it in callback
5. BFF validates returned `state` matches stored value

If an attacker tricks a user into visiting a malicious callback URL, the state validation will fail.

### 5. Encrypted Token Storage

Tokens are encrypted before storing in cookies:

- **Algorithm**: AES-256-GCM (authenticated encryption)
- **Key**: Derived from `COOKIE_SECRET` environment variable
- **Format**: `iv:authTag:encrypted` (all base64-encoded)

Even if an attacker gains access to the cookie, they cannot decrypt the token without the secret.

### 6. HTTP-Only Cookies

Tokens are stored in HTTP-only cookies:

```javascript
res.cookie('access_token', encryptedToken, {
    httpOnly: true,        // Not accessible to JavaScript
    secure: true,          // HTTPS only (production)
    sameSite: 'Strict',    // CSRF protection
    maxAge: 3600000        // 1 hour
});
```

**Benefits:**
- ✅ Protected against XSS attacks
- ✅ Automatically sent with requests
- ✅ `sameSite=Strict` prevents CSRF
- ✅ `secure=true` ensures HTTPS only

### 7. Server-Side Session Storage

PKCE parameters are stored in server memory during OAuth flow:

- **Storage**: In-memory (development) or Redis/PostgreSQL (production)
- **Duration**: Short-lived (10 minutes max)
- **Cleanup**: Deleted immediately after token exchange
- **Session ID**: Stored in HTTP-only cookie

**Why Server-Side?**
- PKCE parameters never exposed to browser
- No risk of XSS stealing OAuth parameters
- Proper separation of concerns

## Production Considerations

### 1. Session Store

The default in-memory session store is **NOT suitable for production**. Use a persistent store:

```javascript
const RedisStore = require('connect-redis')(session);
const redis = require('redis');
const redisClient = redis.createClient();

app.use(session({
    store: new RedisStore({ client: redisClient }),
    secret: SESSION_SECRET,
    // ... other options
}));
```

### 2. Secrets Management

Never hardcode secrets. Use environment variables or a secrets manager:

```bash
# Development
export CLIENT_SECRET="$(openssl rand -base64 32)"
export SESSION_SECRET="$(openssl rand -base64 32)"
export COOKIE_SECRET="$(openssl rand -base64 32)"

# Production
# Use AWS Secrets Manager, HashiCorp Vault, etc.
```

### 3. HTTPS

Always use HTTPS in production:

```javascript
cookie: {
    secure: true,  // Enforce HTTPS
    sameSite: 'Strict'
}
```

### 4. Token Refresh

This example doesn't implement token refresh. For production:

1. Store `refresh_token` in encrypted cookie
2. Implement `/api/refresh` endpoint
3. Automatically refresh expired tokens
4. Handle refresh token rotation

## Comparison: BFF vs. Public Client

| Feature | BFF Pattern (This Example) | Public Client (SPA) |
|---------|---------------------------|---------------------|
| **Client Type** | Confidential | Public |
| **client_secret** | ✅ Yes | ❌ No |
| **PKCE Generation** | Backend | Browser |
| **Token Storage** | Encrypted HTTP-only cookie | localStorage/sessionStorage |
| **XSS Protection** | ✅ Strong | ⚠️ Vulnerable |
| **Token Visibility** | Never exposed to browser | Exposed to JavaScript |
| **Recommended** | ✅ Yes (OAuth 2.0 BCP) | ⚠️ Legacy approach |

## API Endpoints

### Frontend Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Serves the SPA (index.html) |
| `/api/user` | GET | Returns user info from JWT (requires auth cookie) |
| `/api/logout` | POST | Clears authentication cookie |

### OAuth Endpoints (BFF)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/oauth/login` | GET | Initiates OAuth flow (generates PKCE, redirects to auth server) |
| `/oauth/callback` | GET | OAuth callback (exchanges code for token, sets cookie) |

## Troubleshooting

### "Not authenticated" Error

**Cause:** No valid access token in cookie.

**Solution:** Click "Sign In" to initiate OAuth flow.

### "Invalid state" Error

**Cause:** CSRF validation failed (state mismatch).

**Possible reasons:**
- Session expired (10 minute timeout)
- Multiple concurrent login attempts
- Browser cleared cookies mid-flow

**Solution:** Try logging in again.

### "Token exchange failed" Error

**Cause:** Authorization server rejected token exchange.

**Possible reasons:**
- Invalid `client_secret`
- PKCE verification failed
- Authorization code already used
- Authorization code expired

**Solution:** Check server logs and environment variables.

## References

- [RFC 6749 - OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 7636 - PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
- [OAuth 2.0 for Browser-Based Apps](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps)
- [OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics)

## License

Apache-2.0

## Author

abstratium informatique sàrl
