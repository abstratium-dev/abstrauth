# Service-to-Service Authentication Implementation Plan

## Overview

This document provides step-by-step instructions for implementing OAuth 2.0 Client Credentials flow in abstrauth.

**Reference:** [SERVICE_TO_SERVICE_AUTH.md](./SERVICE_TO_SERVICE_AUTH.md)

**Estimated Time:** 2-3 days

---

## Phase 1: Database Schema (30 minutes)

### Step 1.1: Add SERVICE Client Type

**File:** `src/main/resources/db/migration/V01.011__add_service_client_type.sql` ✅ CREATED

```sql
ALTER TABLE T_oauth_clients 
  MODIFY COLUMN client_type VARCHAR(20) NOT NULL 
  COMMENT 'WEB_APPLICATION or SERVICE';

ALTER TABLE T_oauth_clients 
  MODIFY COLUMN allowed_scopes VARCHAR(500) 
  COMMENT 'Space-separated list of scopes allowed for SERVICE clients';
```

**Manual Test to be done by user:**
```bash
mvn flyway:migrate
mysql -u abstrauth -p abstrauth_dev -e "DESCRIBE T_oauth_clients;"
```

### Step 1.2: Create Client Secrets Table

**File:** `src/main/resources/db/migration/V01.012__create_client_secrets_table.sql` ✅ CREATED

```sql
CREATE TABLE T_oauth_client_secrets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(255),
    FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id) ON DELETE CASCADE,
    INDEX idx_client_active (client_id, is_active),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Migrate existing client secrets to new table
INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description)
SELECT client_id, client_secret_hash, TRUE, 'Migrated from T_oauth_clients'
FROM T_oauth_clients 
WHERE client_secret_hash IS NOT NULL;
```

**Manual Test to be done by user:**
```bash
mvn flyway:migrate
mysql -u abstrauth -p abstrauth_dev -e "SELECT COUNT(*) FROM T_oauth_client_secrets;"
# Verify count matches: SELECT COUNT(*) FROM T_oauth_clients WHERE client_secret_hash IS NOT NULL;
```

### Step 1.3: Remove Old client_secret_hash Column

**File:** `src/main/resources/db/migration/V01.013__remove_old_client_secret_column.sql` ✅ CREATED

```sql
-- Remove the old client_secret_hash column from T_oauth_clients
-- This is safe because secrets are now in T_oauth_client_secrets
ALTER TABLE T_oauth_clients DROP COLUMN client_secret_hash;
```

**Manual Test to be done by user:**
```bash
mvn flyway:migrate
mysql -u abstrauth -p abstrauth_dev -e "DESCRIBE T_oauth_clients;"
# Verify client_secret_hash column is gone
```

---

## Phase 2: Backend Core (2 hours)

### Step 2.1: Create ClientSecret Entity ✅ DONE

**File:** `src/main/java/dev/abstratium/abstrauth/entity/ClientSecret.java` ✅ CREATED

JPA entity for `T_oauth_client_secrets` table with all fields.

### Step 2.2: Create ClientSecretRepository ✅ DONE

**File:** `src/main/java/dev/abstratium/abstrauth/repository/ClientSecretRepository.java` ✅ CREATED

Repository using EntityManager pattern with methods:
- `findActiveSecrets(clientId)`
- `findByClientId(clientId)`
- `countActiveSecrets(clientId)`
- `findExpiredSecrets(now)`
- `persist(clientSecret)`
- `findById(id)`

### Step 2.3: Update OAuthClient Entity ✅ DONE

**File:** `src/main/java/dev/abstratium/abstrauth/entity/OAuthClient.java` ✅ UPDATED

Removed `clientSecretHash` field and getters/setters (secrets now in separate table).

### Step 2.4: Update OAuthClientService ✅ DONE

**File:** `src/main/java/dev/abstratium/abstrauth/service/OAuthClientService.java` ✅ UPDATED

- Added `ClientSecretRepository` injection
- Updated `clientSecretMatches()` to check all active secrets
- Updated `createWithSecret()` to persist secret in new table
- Updated `updateClientSecretHash()` to create new secret entry

### Step 2.5: Update TokenResource ✅ DONE

**File:** `src/main/java/dev/abstratium/abstrauth/boundary/oauth/TokenResource.java` ✅ UPDATED

- Added `ClientSecretRepository` injection
- Updated `authenticateClient()` to check all active secrets

### Step 2.6: Fix All Test Files ✅ DONE

Updated all test files to create secrets in `T_oauth_client_secrets` table:
- `AccountsResourceTest.java` ✅
- `TokenRolesTest.java` ✅
- `AccountRoleServiceTest.java` ✅
- `AccountServiceTest.java` ✅
- `AuthorizationServiceTest.java` ✅
- `BootstrapServiceTest.java` ✅
- `OAuthClientServiceTest.java` ✅
- `TokenRevocationServiceTest.java` ✅

### Step 2.7: Fix Dev Configuration ✅ DONE

**File:** `src/main/resources/application.properties` ✅ UPDATED

Added dev profile defaults for Google OAuth to prevent startup errors.

Methods: `findActiveSecrets()`, `countActiveSecrets()`, `findExpiredSecrets()`

### Step 2.5: Implement Client Credentials in TokenResource

**File:** `src/main/java/dev/abstratium/abstrauth/resource/TokenResource.java`

Add handler for `grant_type=client_credentials`:
- Validate client type is SERVICE
- Check secret against all active secrets
- Validate requested scopes
- Generate token with service claims (no user info)
- Return token (no refresh token)

### Step 2.6: Update Discovery Endpoint

**File:** `src/main/java/dev/abstratium/abstrauth/resource/WellKnownResource.java`

Add `client_credentials` to `grant_types_supported`.

**Test:**
```bash
mvn compile
mvn test
curl http://localhost:8080/.well-known/oauth-authorization-server | jq .grant_types_supported
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

## Phase 4: Angular UI (3 hours)

### Step 4.1: Update Models

**File:** `src/main/webui/src/app/models/client.model.ts`

Add: `ClientType.SERVICE`, `allowedScopes`, secret management interfaces

### Step 4.2: Update Client Service

**File:** `src/main/webui/src/app/services/client.service.ts`

Add: `listSecrets()`, `createSecret()`, `revokeSecret()`

### Step 4.3: Update Create Client Form

**File:** `src/main/webui/src/app/components/create-client/`

- Add client type dropdown
- Show redirect URIs for WEB_APPLICATION
- Show scope selection for SERVICE
- Display secret after creation

### Step 4.4: Add Secret Management Component

**Create:** `src/main/webui/src/app/components/client-secrets/`

- List secrets with metadata
- Generate new secret button
- Revoke secret button
- Show warnings for expiring secrets

**Test:**
```bash
cd src/main/webui
npm run build
```

---

## Phase 5: Testing (2 hours)

### Step 5.1: Unit Tests

**Create:** `src/test/java/dev/abstratium/abstrauth/resource/ClientCredentialsFlowTest.java`

Tests:
- ✅ Valid client credentials → 200 with token
- ✅ Invalid secret → 401
- ✅ Invalid scope → 400
- ✅ Wrong client type → 401
- ✅ Client not found → 401

### Step 5.2: Integration Tests

**Create:** `src/test/java/dev/abstratium/abstrauth/resource/SecretRotationTest.java`

Tests:
- ✅ Zero-downtime rotation (both secrets work)
- ✅ Cannot revoke last secret
- ✅ Revoked secret stops working

**Run:**
```bash
mvn test
mvn verify  # Check coverage
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

---

# TODO at end

- Update USER GUIDE to describe secret rotation
- 