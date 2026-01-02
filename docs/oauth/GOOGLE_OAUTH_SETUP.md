# Google OAuth Setup Guide

## Overview

Google federated login has been successfully implemented. Users can now sign in using their Google accounts.

## What Was Implemented

### 1. Database Changes
- Added `picture` and `auth_provider` fields to `T_accounts` table
- Created `T_federated_identities` table to link accounts with external identity providers
- Migrations: `V01.008` and `V01.009`

### 2. New Entities
- `FederatedIdentity` - Links accounts to external providers (Google, Microsoft, GitHub, etc.)
- Updated `Account` entity with `picture` and `authProvider` fields

### 3. Services
- `GoogleOAuthService` - Handles Google OAuth flow
- `FederatedIdentityService` - Manages federated identity links
- `GoogleOAuthClient` - REST client for Google APIs
- Updated `AccountService` with methods for federated accounts

### 4. API Endpoints
- `GET /oauth2/federated/google?request_id={id}` - Initiates Google login
- `GET /oauth2/callback/google` - Handles Google OAuth callback

### 5. Tests
- Comprehensive WireMock-based tests in `GoogleOAuthFlowTest`
- Tests cover: new user creation, existing user linking, error handling
- All 5 tests passing ✓

## Configuration Required

### Step 1: Create Google OAuth Application

1. Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Create a new project or select an existing one
3. Enable the "Google+ API" or "Google Identity" API
4. Go to "Credentials" → "Create Credentials" → "OAuth 2.0 Client ID"
5. Application type: "Web application"
6. Add authorized redirect URIs:
   - Development: `http://localhost:8080/oauth2/callback/google`
   - Production: `https://auth.abstratium.dev/oauth2/callback/google`
7. Save and copy the Client ID and Client Secret

### Step 2: Update application.properties

Replace the placeholders in `src/main/resources/application.properties`:

```properties
# For development
%dev.oauth.google.client-id=YOUR_GOOGLE_CLIENT_ID_HERE
%dev.oauth.google.client-secret=YOUR_GOOGLE_CLIENT_SECRET_HERE

# For production (set via environment variables)
%prod.oauth.google.client-id=SET-USING-ENV-VAR
%prod.oauth.google.client-secret=SET-USING-ENV-VAR
```

### Step 3: Set Production Environment Variables

For production deployment, set these environment variables:

```bash
OAUTH_GOOGLE_CLIENT_ID=your_production_client_id
OAUTH_GOOGLE_CLIENT_SECRET=your_production_client_secret
```

## How It Works

### User Flow

1. User visits your app and clicks "Sign in with Google"
2. App initiates OAuth flow: `GET /oauth2/authorize?...`
3. Server redirects to Angular app: `/signin/{requestId}`
4. Angular app calls: `GET /oauth2/federated/google?request_id={requestId}`
5. Server redirects to Google for authentication
6. User authenticates with Google
7. Google redirects back to: `/oauth2/callback/google?code=...&state={requestId}`
8. Server:
   - Exchanges code for Google user info
   - Creates new account OR links to existing account (by email)
   - Generates authorization code
   - Redirects to client app
9. Client exchanges authorization code for JWT access token

### Account Linking Strategy

- **New User**: Creates account with Google email, name, and picture
- **Existing User (same email)**: Links Google identity to existing account
- **Returning User**: Recognizes Google identity and logs in directly

### Security Features

- PKCE (Proof Key for Code Exchange) support
- State parameter validation
- Google ID token verification
- Secure token exchange
- Account linking with email verification

## Testing

Run the tests:

```bash
# Run all Google OAuth tests
mvn test -Dtest=GoogleOAuthFlowTest

# Run specific test
mvn test -Dtest=GoogleOAuthFlowTest#testGoogleOAuthFlowNewUser
```

All tests use WireMock to mock Google's OAuth endpoints, so no real Google credentials are needed for testing.

## Database Schema

### T_accounts
```sql
- id (VARCHAR 36, PK)
- email (VARCHAR 255, UNIQUE, NOT NULL)
- email_verified (BOOLEAN, DEFAULT FALSE)
- name (VARCHAR 255)
- picture (VARCHAR 500)              -- NEW: Profile picture URL
- auth_provider (VARCHAR 50)         -- NEW: "native", "google", etc.
- created_at (TIMESTAMP)
```

### T_federated_identities
```sql
- id (VARCHAR 36, PK)
- account_id (VARCHAR 36, FK → T_accounts.id)
- provider (VARCHAR 50, NOT NULL)    -- "google", "microsoft", "github"
- provider_user_id (VARCHAR 255)     -- Google user ID (sub claim)
- email (VARCHAR 255)                -- Email from provider
- connected_at (TIMESTAMP)
- UNIQUE(provider, provider_user_id)
```

## Next Steps

### To Add Microsoft/GitHub Login

1. Create similar services: `MicrosoftOAuthService`, `GitHubOAuthService`
2. Add REST clients: `MicrosoftOAuthClient`, `GitHubOAuthClient`
3. Add endpoints: `/oauth2/federated/microsoft`, `/oauth2/callback/microsoft`
4. Add configuration properties
5. Create tests with WireMock

The architecture is designed to make adding new providers straightforward.

## Troubleshooting

### "Invalid redirect_uri" error
- Ensure the redirect URI in Google Console exactly matches the one in your config
- Check for trailing slashes
- Verify the protocol (http vs https)

### "Invalid client_id" error
- Double-check the client ID in application.properties
- Ensure you're using the correct profile (dev/prod)

### Account not created
- Check database migrations have run
- Verify email is not already in use
- Check server logs for detailed error messages

## Documentation

Updated documentation:
- `FEDERATED_LOGIN.md` - Complete federated login flow documentation
- `FLOWS.md` - OAuth 2.0 flows (unchanged, focuses on native flow)
- `README.md` - Add Google OAuth to feature list

## API Documentation

The OpenAPI documentation is automatically updated and available at:
- Development: http://localhost:8080/q/swagger-ui
- Look for the "OAuth 2.0 Federated Login" tag
