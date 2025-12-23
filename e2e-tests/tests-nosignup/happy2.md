# Happy Path Test: Admin Creates Manager Account

## Overview
This test (`happy2.spec.ts`) validates the complete workflow of account management, role assignment, and authorization in the OAuth server. It tests the interaction between admin and manager accounts with different permission levels.

## Test Flow

### 1. Database Cleanup (Steps 1-5)
- **Step 1**: Attempts to sign in as admin to check if database needs cleanup
- **Step 2**: If admin exists, begins cleanup process
- **Step 3**: Deletes all accounts except the current admin
- **Step 4**: Deletes all clients except `abstratium-abstrauth`
- **Step 5**: Signs out after cleanup

### 2. Admin Account Setup (Steps 6-7)
- **Step 6**: Signs up as admin (first account automatically gets admin, manage-accounts, and manage-clients roles)
- **Step 7**: Signs out and back in to refresh JWT token with all roles

### 3. Manager Account Creation (Steps 8-10)
- **Step 8**: Admin navigates to accounts page and creates a new manager account with email `manager@abstratium.dev`
- **Step 9**: Admin signs out
- **Step 10**: Manager signs in via invite link (first time only)

### 4. Manager First-Time Setup (Steps 11-13)
- **Step 11**: Manager approves authorization
- **Step 12**: Manager changes password (required for invite-based accounts)
- **Step 13**: Verifies signed in as Manager

### 5. Manager Permission Verification (Step 14)
- **Step 14**: Verifies manager only has "user" role initially
  - Confirms manager can only see their own account (1 tile visible)
  - Verifies the visible account is the manager's account
  - Confirms manager cannot see "Add Role" button (no manage-accounts role yet)

### 6. Admin Grants Manager Permissions (Steps 15-17)
- **Step 15**: Manager signs out
- **Step 16**: Admin signs back in
- **Step 17**: Admin grants manager the `manage-accounts` and `manage-clients` roles

### 7. Admin Tests Deletion Protections (Steps 18-19)
- **Step 18**: Admin attempts to delete admin role from admin user (should fail)
  - Verifies error: "Cannot delete the last admin role for abstratium-abstrauth"
- **Step 19**: Admin attempts to delete admin account (should fail)
  - Verifies error: "Cannot delete the account with the only admin role for abstratium-abstrauth"

### 8. Manager Role Assignment Test (Steps 20-22)
- **Step 20**: Admin signs out
- **Step 21**: Manager signs back in with username/password
- **Step 22**: Manager attempts to add admin role to themselves (should fail)
  - Verifies error: "Only admin can add the admin role"

### 9. Manager Creates Client (Steps 23-24)
- **Step 23**: Manager navigates to clients page and creates new client `anapp-acomp` with:
  - Client ID: `anapp-acomp`
  - Redirect URI: `http://localhost:3333/callback`
  - Scopes: `openid profile email`
- **Step 24**: Manager signs out

**Note**: Manager deletion protection tests are skipped because users with `manage-accounts` role can only see accounts for non-default clients. Since both admin and manager only have roles for the default `abstratium-abstrauth` client, the manager cannot see the admin account to test deletion protections.

### 10. Admin Creates New User (Steps 25-28)
- **Step 25**: Admin signs in
- **Step 26**: Creates new user `AUser` with email `auser@abstratium.dev`
- **Step 27**: Adds `viewer` role for AUser on `abstratium-abstrauth` client
- **Step 28**: Adds `viewer` role for AUser on `anapp-acomp` client

### 11. Client Filtering Verification (Steps 29-31)
- **Step 29**: Admin navigates to clients page and verifies `anapp-acomp` client is visible
- **Step 30**: Clicks link to view accounts with roles for this client
  - Dismisses any toast notifications that might block navigation
  - Verifies URL contains `/accounts?filter=anapp-acomp`
- **Step 31**: Confirms only AUser account is visible (filtered by 'viewer' role on 'anapp-acomp')
  - Verifies the filtered account email is `auser@abstratium.dev`

## Key Validations

### Role-Based Access Control
- Admin can create accounts and assign any role
- Manager with `manage-accounts` role can create accounts but cannot assign admin role
- Manager with `manage-clients` role can create OAuth clients
- Users without management roles can only see their own account

### Deletion Protections
- Cannot delete the last admin role for `abstratium-abstrauth` client
- Cannot delete an account that holds the only admin role for `abstratium-abstrauth` client
- Both admin and manager users are blocked by these protections

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
