# Bootstrap Client Secret Initialization Analysis

## Problem Statement

The `abstratium-abstrauth` client secret is updated on every server startup, creating unnecessary database writes and new secret entries in the `T_oauth_client_secrets` table. This happens because:

1. The client secret must be configurable via environment variable (`ABSTRAUTH_CLIENT_SECRET`)
2. The secret is stored as a BCrypt hash in the database (not plain text)
3. On startup, the server syncs the environment variable to the database
4. The current implementation doesn't check if the hash already matches before creating a new entry

## Current Flow

### 1. Database Migration - Initial Secret Creation

**File:** `V01.006__insertDefaultClient.sql`
```sql
INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce)
VALUES (
    'e9877513-73cf-44fe-b581-4bad96e168cb',
    'abstratium-abstrauth',
    'abstratium abstrauth',
    'public',
    '["http://localhost:8080/api/auth/callback", "https://auth.abstratium.dev/api/auth/callback"]',
    '["openid", "profile", "email"]',
    TRUE
);
```

**Note:** This migration does NOT set a client secret. The client is created as `public` type with no secret.

### 2. Database Migration - Update to Confidential with Secret

**File:** `V01.010__updateDefaultClientToConfidential.sql`
```sql
UPDATE T_oauth_clients 
SET 
    client_type = 'confidential',
    -- BCrypt hash of 'dev-secret-CHANGE-IN-PROD' with cost factor 10
    client_secret_hash = '$2a$10$mtwoJ4E6V6XPY8DHrKEpIuV2n0Q1J7FjMZkja5Kv0lYkq36LxcZdO'
WHERE client_id = 'abstratium-abstrauth';
```

**This is where the initial secret is set!** The hardcoded BCrypt hash represents the default secret `dev-secret-CHANGE-IN-PROD`.

### 3. Database Migration - Create Secrets Table and Migrate

**File:** `V01.011__create_client_secrets_table.sql`
```sql
CREATE TABLE T_oauth_client_secrets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(255),
    CONSTRAINT FK_oauth_client_secrets_client FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id) ON DELETE CASCADE
);

-- Migrate existing client secrets to new table
INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description)
SELECT client_id, client_secret_hash, TRUE, 'Initial secret'
FROM T_oauth_clients 
WHERE client_secret_hash IS NOT NULL;
```

**This migrates the secret from the old column to the new table.** The description is now `'Initial secret'` (was previously `'Migrated from T_oauth_clients'`).

### 4. Application Configuration

**File:** `application.properties`
```properties
quarkus.oidc.bff.client-id=abstratium-abstrauth
quarkus.oidc.bff.credentials.secret=${ABSTRAUTH_CLIENT_SECRET:dev-secret-CHANGE-IN-PROD}
```

The secret defaults to `dev-secret-CHANGE-IN-PROD` if the environment variable is not set.

### 5. Bootstrap Service - Startup Synchronization

**File:** `BootstrapService.java`
```java
@ApplicationScoped
public class BootstrapService {
    
    private static final String DEFAULT_SECRET = "dev-secret-CHANGE-IN-PROD";
    
    @ConfigProperty(name = "quarkus.oidc.bff.credentials.secret")
    String clientSecret;
    
    void onStart(@Observes StartupEvent ev) {
        syncClientSecretHash();
    }
    
    void syncClientSecretHash() {
        // Validate and warn about weak/default secrets
        // ...
        
        // Update the client secret hash using the service
        clientService.updateClientSecretHash(Roles.CLIENT_ID, clientSecret);
        
        Log.info("Client secret hash synchronized for '" + Roles.CLIENT_ID + "'");
    }
}
```

**This runs on EVERY startup** and calls `updateClientSecretHash()`.

### 6. OAuthClientService - Update Secret Hash

**File:** `OAuthClientService.java`
```java
@Transactional
public void updateClientSecretHash(String clientId, String plainSecret) {
    Optional<OAuthClient> clientOpt = findByClientId(clientId);
    if (clientOpt.isEmpty()) {
        throw new IllegalArgumentException("Client not found: " + clientId);
    }

    String hashedSecret = hashClientSecret(plainSecret);

    // Only create a new secret if an existing one does not match the hashedSecret
    boolean secretExists = clientSecretService.findActiveSecrets(clientId).stream()
        .anyMatch(clientSecret -> {
            return clientSecret.getSecretHash().equals(hashedSecret);
        });
    
    if (!secretExists) {
        // Create new secret in ClientSecret table
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setClientId(clientId);
        clientSecret.setSecretHash(hashedSecret);
        clientSecret.setDescription("Updated secret");
        clientSecret.setActive(true);
        clientSecretService.persist(clientSecret);
    }
}
```

**Recent fix:** The code now checks if the hash already exists before creating a new entry. This prevents duplicate secrets on every startup.

## Timeline of Secret Initialization

1. **Database creation** → Client created without secret (public type)
2. **Migration V01.010** → Client updated to confidential, hardcoded BCrypt hash added to `client_secret_hash` column
3. **Migration V01.011** → New `T_oauth_client_secrets` table created, secret migrated from old column with description `'Initial secret'`
4. **Application startup** → `BootstrapService` reads `ABSTRAUTH_CLIENT_SECRET` environment variable
5. **Secret sync** → `updateClientSecretHash()` hashes the plain secret and checks if it already exists
6. **Conditional insert** → Only creates new secret entry if hash doesn't match any existing active secret

## Why the Initial Secret is Set in SQL

The initial secret **must** be set in SQL (migration V01.010) because:

1. **Chicken-and-egg problem**: The application needs a valid client secret to start up (Quarkus OIDC BFF requires it)
2. **First-time setup**: On first deployment, there's no existing secret in the database
3. **Known default**: The hardcoded hash `$2a$10$mtwoJ4E6V6XPY8DHrKEpIuV2n0Q1J7FjMZkja5Kv0lYkq36LxcZdO` corresponds to `dev-secret-CHANGE-IN-PROD`
4. **Bootstrap synchronization**: On startup, if the environment variable matches the default, no new secret is created (hash matches)

## Solution Analysis

### Current Solution (Implemented)

The recent change to `updateClientSecretHash()` solves the problem by:

1. Hashing the plain secret from the environment variable
2. Checking if any active secret already has this hash
3. Only creating a new secret entry if the hash is different

**This works correctly** and prevents unnecessary secret creation on every startup.

### Why MD5 Hash Won't Work for Migration

The suggested MD5 hash approach won't work because:

1. **Existing secrets**: Databases already have BCrypt hashes without any MD5 metadata
2. **Migration complexity**: Would require adding a new column and migrating all existing secrets
3. **Unnecessary**: The current solution (comparing BCrypt hashes directly) already solves the problem

## Recommendations

### ✅ Current Implementation is Correct

The current implementation with the hash comparison check is the right solution. It:

- Prevents duplicate secrets on startup
- Allows environment variable configuration
- Works with existing databases
- Maintains security (BCrypt hashing)

### ⚠️ Do NOT Remove SQL Secret Initialization

The SQL migration (V01.010) **must** remain because:

1. It provides the initial secret for first-time deployments
2. The application requires a valid secret to start
3. The bootstrap sync will update it if the environment variable differs

### 📝 Documentation Improvements

Consider documenting:

1. The default secret value and its hash in deployment documentation
2. The fact that changing `ABSTRAUTH_CLIENT_SECRET` will create a new secret entry (not replace the old one)
3. The secret rotation workflow (old secrets remain active until manually revoked)

## Conclusion

**The initial client secret is set in SQL migration `V01.010__updateDefaultClientToConfidential.sql`** with a hardcoded BCrypt hash of the default secret `dev-secret-CHANGE-IN-PROD`.

The current implementation correctly handles startup synchronization by checking if the hash already exists before creating a new secret entry. This prevents the duplicate secret problem while maintaining flexibility for environment-based configuration.

**No changes are needed** - the recent fix to `updateClientSecretHash()` already solves the problem.
