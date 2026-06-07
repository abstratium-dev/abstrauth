-- Test OAuth clients for integration tests
-- org_id is set to the default org so that the client-org ownership check passes in tests
INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at, org_id) 
VALUES ('client-a-id', 'client-a', 'Test Client A', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at, org_id) 
VALUES ('client-b-id', 'client-b', 'Test Client B', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at, org_id) 
VALUES ('abstratium-abstrauth-id', 'abstratium-abstrauth', 'Abstratium Abstrauth', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at, org_id) 
VALUES ('test-client-id', 'test-client', 'Test Client', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at, org_id) 
VALUES ('test_client-id', 'test_client', 'Test Client Underscore', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at, org_id) 
VALUES ('client-unique-id', 'client-unique', 'Client Unique', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at, org_id) 
VALUES ('client-different-id', 'client-different', 'Client Different', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at, org_id) 
VALUES ('client-x-id', 'client-x', 'Client X', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at, org_id) 
VALUES ('client-y-id', 'client-y', 'Client Y', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at, org_id) 
VALUES ('client-z-id', 'client-z', 'Client Z', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');
