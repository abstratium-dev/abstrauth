-- Repeatable migration for test data
-- This runs after all versioned migrations to ensure default org exists and test clients are loaded

-- Ensure default organization exists (required for all test data)
INSERT INTO T_organisations (id, name, created_at)
SELECT '00000000-0000-0000-0000-000000000000', 'Default Test Org', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM T_organisations WHERE id = '00000000-0000-0000-0000-000000000000');

-- Test OAuth clients for integration tests
-- org_id is set to the default org so that the client-org ownership check passes in tests
INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, created_at, org_id)
SELECT 'client-a-id', 'client-a', 'Test Client A', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = 'client-a');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, created_at, org_id)
SELECT 'client-b-id', 'client-b', 'Test Client B', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = 'client-b');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, created_at, org_id)
SELECT 'abstratium-abstrauth-id', 'abstratium-abstrauth', 'Abstratium Abstrauth', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = 'abstratium-abstrauth');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, created_at, org_id)
SELECT 'test-client-id', 'test-client', 'Test Client', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = 'test-client');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, created_at, org_id)
SELECT 'test_client-id', 'test_client', 'Test Client Underscore', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = 'test_client');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, created_at, org_id)
SELECT 'client-unique-id', 'client-unique', 'Client Unique', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = 'client-unique');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, created_at, org_id)
SELECT 'client-different-id', 'client-different', 'Client Different', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = 'client-different');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, created_at, org_id)
SELECT 'client-x-id', 'client-x', 'Client X', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = 'client-x');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, created_at, org_id)
SELECT 'client-y-id', 'client-y', 'Client Y', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = 'client-y');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, created_at, org_id)
SELECT 'client-z-id', 'client-z', 'Client Z', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_clients WHERE client_id = 'client-z');

-- Insert client secrets for test clients
INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, created_at, org_id)
SELECT 'client-a', '$2a$10$dummyhash', TRUE, 'Test secret', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_client_secrets WHERE client_id = 'client-a');

INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, created_at, org_id)
SELECT 'client-b', '$2a$10$dummyhash', TRUE, 'Test secret', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_client_secrets WHERE client_id = 'client-b');

INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, created_at, org_id)
SELECT 'test-client', '$2a$10$dummyhash', TRUE, 'Test secret', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_client_secrets WHERE client_id = 'test-client');

INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, created_at, org_id)
SELECT 'test_client', '$2a$10$dummyhash', TRUE, 'Test secret', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_client_secrets WHERE client_id = 'test_client');

INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, created_at, org_id)
SELECT 'client-unique', '$2a$10$dummyhash', TRUE, 'Test secret', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_client_secrets WHERE client_id = 'client-unique');

INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, created_at, org_id)
SELECT 'client-different', '$2a$10$dummyhash', TRUE, 'Test secret', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_client_secrets WHERE client_id = 'client-different');

INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, created_at, org_id)
SELECT 'client-x', '$2a$10$dummyhash', TRUE, 'Test secret', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_client_secrets WHERE client_id = 'client-x');

INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, created_at, org_id)
SELECT 'client-y', '$2a$10$dummyhash', TRUE, 'Test secret', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_client_secrets WHERE client_id = 'client-y');

INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description, created_at, org_id)
SELECT 'client-z', '$2a$10$dummyhash', TRUE, 'Test secret', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000'
WHERE NOT EXISTS (SELECT 1 FROM T_oauth_client_secrets WHERE client_id = 'client-z');
