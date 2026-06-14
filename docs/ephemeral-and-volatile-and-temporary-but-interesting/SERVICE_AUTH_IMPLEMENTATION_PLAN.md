# Service-to-Service Authentication Implementation Plan

> **Status: COMPLETE.** All phases implemented. The role mechanism evolved from flat `ServiceAccountRole` / `T_service_account_roles` to `ClientRole` / `T_client_roles` for tenant isolation and role governance. See [SERVICE_ROLES_SOLUTION.md](./SERVICE_ROLES_SOLUTION.md) for the decision.

## Overview

This document provides step-by-step instructions for implementing OAuth 2.0 Client Credentials flow in abstrauth.

**Reference:** [SERVICE_TO_SERVICE_AUTH.md](./SERVICE_TO_SERVICE_AUTH.md)

---

## Phase 1: Database Schema ✅ COMPLETE

### Step 1.1: Add SERVICE Client Type ✅ DONE

**File:** `src/main/resources/db/migration/V01.011__create_client_secrets_table.sql` ✅ EXISTS

Already contains client_type and allowed_scopes columns.

### Step 1.2: Create Client Secrets Table ✅ DONE

**File:** `src/main/resources/db/migration/V01.011__create_client_secrets_table.sql` ✅ EXISTS

Table created with MySQL/H2 compatible SQL (no ENGINE or CHARSET clauses).

### Step 1.3: Remove Old client_secret_hash Column ✅ DONE

**File:** `src/main/resources/db/migration/V01.012__remove_old_client_secret_column.sql` ✅ EXISTS

Column removed successfully.

### Step 1.4: Create Client Roles Table ✅ DONE

**File:** `src/main/resources/db/migration/V01.032__create_client_roles_table.sql` ✅ EXISTS

Table `T_client_roles` with `src_client_id`, `target_client_id`, `role`, `org_id` (Hibernate `@TenantId`).

**Note:** An earlier iteration used `T_service_account_roles` (flat, per-client roles). This was replaced by `T_client_roles` to provide tenant isolation via `@TenantId` and to enforce that roles must be declared in `T_client_allowed_roles` for the target client.

---

## Phase 2: Backend Core - Client Roles ✅ COMPLETE

### Step 2.1: Create ClientRole Entity ✅ DONE

**File:** `src/main/java/dev/abstratium/abstrauth/entity/ClientRole.java` ✅ EXISTS

JPA entity for `T_client_roles` with `@TenantId` on `org_id`.

### Step 2.2: Create ClientRoleService ✅ DONE

**File:** `src/main/java/dev/abstratium/abstrauth/service/ClientRoleService.java` ✅ EXISTS

Key method: `findBySrcClientId(srcClientId)` — returns `List<ClientRole>`, auto-filtered by `@TenantId`.

### Step 2.3: Implement Client Credentials in TokenResource ✅ DONE

**File:** `src/main/java/dev/abstratium/abstrauth/non_multitenancy/boundary/TokenResource.java` ✅ UPDATED

- Validates client credentials
- Calls `ClientRoleService.findBySrcClientId()` with the client's own `orgId` as tenant context
- Generates JWT with `scope`, `groups`, `orgId` claims

### Step 2.4: Discovery Endpoint ✅ DONE

`client_credentials` included in `grant_types_supported`.

**Test:**
```bash
mvn clean test
```

---

## Phase 3: Client Management API (2 hours) ✅ COMPLETE

### Step 3.1: Create ClientSecretsResource ✅ DONE

**File:** `src/main/java/dev/abstratium/abstrauth/boundary/api/ClientSecretsResource.java` ✅ CREATED

DTOs (as static inner classes with `@RegisterForReflection`):
- `CreateSecretRequest` - Request to create new secret
- `CreateSecretResponse` - Response with plain secret (shown once)
- `SecretInfo` - Secret metadata (no plain value)

Endpoints implemented:
- `POST /api/clients/{clientId}/secrets` - Generate new secret ✅
- `GET /api/clients/{clientId}/secrets` - List secrets (metadata only) ✅
- `DELETE /api/clients/{clientId}/secrets/{secretId}` - Revoke secret ✅

Features:
- Admin role required for all operations
- Cannot revoke last active secret
- Optional expiration date support
- Proper error handling (404, 400, 403)

### Step 3.2: Create Tests ✅ DONE

**File:** `src/test/java/dev/abstratium/abstrauth/boundary/api/ClientSecretsResourceTest.java` ✅ CREATED

Tests implemented (8 tests, all passing):
- `testListSecrets` - List all secrets for a client ✅
- `testListSecretsNotFound` - 404 for non-existent client ✅
- `testCreateSecret` - Create new secret with expiration ✅
- `testCreateSecretWithoutExpiration` - Create permanent secret ✅
- `testRevokeSecret` - Deactivate a secret ✅
- `testCannotRevokeLastSecret` - Prevent revoking last secret ✅
- `testRevokeSecretNotFound` - 404 for non-existent secret ✅
- `testRevokeSecretWrongClient` - 404 for wrong client ✅

### Step 3.3: Configuration Updates ✅ DONE

**File:** `src/test/resources/application.properties` ✅ UPDATED

- Set test port to 10080 to avoid conflicts with port 8081

---

## Phase 4: Angular UI ✅ COMPLETE

### Step 4.1: Models ✅ DONE

`model.service.ts` contains `ClientRole`, `ClientRolesResponse`, `AddClientRoleRequest` interfaces.

### Step 4.2: Controller ✅ DONE

`controller.ts` implements `listClientRoles()`, `addClientRole()`, `removeClientRole()`.

### Step 4.3: ClientsComponent ✅ DONE

`clients.component.ts` / `.html` manages client roles via the **Client Roles** section:
- View all client roles for a source client
- Add a client role (select target client → select role from allowed-roles dropdown)
- Remove a client role with confirmation

**Note:** The earlier `ServiceAccountRole` UI ("Manage Roles" button, flat role list) was removed. Client roles replace it entirely.

---

## Phase 5: Testing ✅ COMPLETE

### Step 5.1: TokenResourceTest ✅ DONE

**File:** `src/test/java/dev/abstratium/abstrauth/non_multitenancy/boundary/TokenResourceTest.java`

Covers:
- Valid client credentials → 200 with token containing `groups` from `ClientRole` records
- `groups` format: `targetClientId_role` with org prefix stripped
- Cross-org "hack" attempt → roles from another org are NOT included
- No client roles → empty `groups` claim
- Invalid secret → 401
- Invalid scope → 400

### Step 5.2: ClientRoleServiceTest ✅ DONE

**File:** `src/test/java/dev/abstratium/abstrauth/service/ClientRoleServiceTest.java`

**Run:**
```bash
mvn test
```

---

## Manual Testing Checklist

### Test 1: Create SERVICE Client
1. Start: `mvn quarkus:dev`
2. Login to UI
3. Create client with type=SERVICE, scopes=api:read
4. ✅ Verify secret is shown once
5. ✅ Copy secret

### Test 2: Get Token
```bash
curl -X POST http://localhost:8080/oauth2/token \
  -d "grant_type=client_credentials" \
  -d "client_id=YOUR_CLIENT_ID" \
  -d "client_secret=YOUR_SECRET" \
  -d "scope=api:read"
```
✅ Verify you get access_token

### Test 3: Use Token
```bash
curl http://localhost:8080/api/clients \
  -H "Authorization: Bearer YOUR_TOKEN"
```
✅ Verify token works

### Test 4: Secret Rotation
1. Generate new secret in UI
2. Test both old and new secrets work
3. Revoke old secret
4. ✅ Verify old secret fails, new works

### Test 5: Invalid Scenarios
- ✅ Wrong secret → 401
- ✅ Invalid scope → 400
- ✅ WEB_APPLICATION client with client_credentials → 401

---

## Deployment Checklist

### Before Deployment
- ✅ All tests pass
- ✅ Coverage ≥ 80%
- ✅ Manual tests completed
- ✅ Documentation updated

### Production Steps
1. Backup database
2. Run migrations
3. Deploy new version
4. Verify discovery endpoint
5. Create test SERVICE client
6. Test token generation
7. Monitor logs for errors

---

## Rollback Plan

If issues occur:

1. **Database:** Migrations are additive, no rollback needed
2. **Code:** Revert to previous version
3. **Existing clients:** Continue to work (backward compatible)

---

## Summary

**What Changes:**
- ✅ New SERVICE client type
- ✅ Multiple secrets per client
- ✅ Client credentials grant type
- ✅ Secret management UI

**What Stays the Same:**
- ✅ Existing WEB_APPLICATION clients work unchanged
- ✅ Authorization code flow unchanged
- ✅ User authentication unchanged

**Testing Focus:**
- Client credentials flow
- Secret rotation
- Scope validation
- UI functionality

---

## Notes

- Table naming convention uses `T_` prefix (e.g., `T_oauth_clients`, `T_oauth_client_secrets`)
- The `client_secret_hash` column is removed from `T_oauth_clients` after migration to new table
- All existing clients will continue to work during and after migration
- Migration files created: V01.011, V01.012, V01.013 

