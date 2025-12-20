# Happy Path Test: Admin Creates Manager Account

## Overview
This test (`happy2.spec.ts`) validates the complete workflow of account management, role assignment, and authorization in the OAuth server. It tests the interaction between admin and manager accounts with different permission levels.

## Test Flow

### 1. Database Cleanup (Steps 1-2)
- Attempts to sign in as admin to check if database needs cleanup
- If admin exists:
  - Deletes all accounts except the current admin
  - Deletes all clients except `abstratium-abstrauth`
  - Signs out after cleanup

### 2. Admin Account Setup (Steps 3-3a)
- Signs up as admin (first account automatically gets admin, manage-accounts, and manage-clients roles)
- Signs out and back in to refresh JWT token with all roles

### 3. Manager Account Creation (Steps 4-6)
- Admin navigates to accounts page
- Creates a new manager account with email `manager@abstratium.dev`
- Admin signs out
- Manager signs in via invite link (first time only)

### 4. Manager First-Time Setup (Steps 7-9)
- Manager approves authorization
- Manager changes password (required for invite-based accounts)
- Verifies signed in as Manager

### 5. Manager Permission Verification (Step 10)
- Verifies manager only has "user" role initially
- Confirms manager can only see their own account (1 tile visible)
- Verifies the visible account is the manager's account
- Confirms manager cannot see "Add Role" button (no manage-accounts role yet)

### 6. Admin Grants Manager Permissions (Steps 11-14)
- Manager signs out
- Admin signs back in
- Admin grants manager the `manage-accounts` and `manage-clients` roles
- Admin signs out

### 7. Manager Role Assignment Test (Steps 15-16)
- Manager signs back in with username/password
- Manager attempts to add admin role to themselves (should fail)
- Verifies error: "Only admin can add the admin role"

### 8. Manager Creates Client (Steps 17-18)
- Manager navigates to clients page
- Creates new client `anapp-acomp` with:
  - Client ID: `anapp-acomp`
  - Redirect URI: `http://localhost:3333/callback`
  - Scopes: `openid profile email`
- Manager signs out

### 9. Admin Creates New User (Steps 19-22)
- Admin signs in
- Creates new user `AUser` with email `auser@abstratium.dev`
- Adds `viewer` role for AUser on `abstratium-abstrauth` client
- Adds `viewer` role for AUser on `anapp-acomp` client

### 10. Client Filtering Verification (Steps 23-25)
- Admin navigates to clients page
- Verifies `anapp-acomp` client is visible
- Dismisses any toast notifications that might block navigation
- Clicks link to view accounts with roles for this client
- Verifies URL contains `/accounts?filter=anapp-acomp`
- Confirms only AUser account is visible (filtered by 'viewer' role on 'anapp-acomp')
- Verifies the filtered account email is `auser@abstratium.dev`

## Key Validations

### Role-Based Access Control
- Admin can create accounts and assign any role
- Manager with `manage-accounts` role can create accounts but cannot assign admin role
- Manager with `manage-clients` role can create OAuth clients
- Users without management roles can only see their own account

### Account Visibility
- Users without `manage-accounts` role see only their own account
- Users with `manage-accounts` role see all accounts
- Client filtering shows only accounts with roles for that specific client

### Invite-Based Account Flow
- Invite links pre-fill email address
- First-time sign-in requires password change
- Authorization approval is required before password change

## Test Data
- **Admin**: `admin@abstratium.dev` / `secretLong` / `Admin`
- **Manager**: `manager@abstratium.dev` / `secretLong2` / `Manager`
- **AUser**: `auser@abstratium.dev` / (invite-based) / `AUser`
- **Default Client**: `abstratium-abstrauth`
- **Test Client**: `anapp-acomp`
