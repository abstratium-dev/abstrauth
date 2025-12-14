-- Test OAuth clients for integration tests
INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at) 
VALUES ('client-a-id', 'client-a', 'Test Client A', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP);

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at) 
VALUES ('client-b-id', 'client-b', 'Test Client B', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP);

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at) 
VALUES ('abstratium-abstrauth-id', 'abstratium-abstrauth', 'Abstratium Abstrauth', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP);

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at) 
VALUES ('test-client-id', 'test-client', 'Test Client', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP);

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at) 
VALUES ('test_client-id', 'test_client', 'Test Client Underscore', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP);

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at) 
VALUES ('client-unique-id', 'client-unique', 'Client Unique', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP);

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at) 
VALUES ('client-different-id', 'client-different', 'Client Different', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP);

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at) 
VALUES ('client-x-id', 'client-x', 'Client X', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP);

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at) 
VALUES ('client-y-id', 'client-y', 'Client Y', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP);

INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, client_secret_hash, created_at) 
VALUES ('client-z-id', 'client-z', 'Client Z', 'confidential', '["http://localhost:8080/callback"]', '["openid","profile","email"]', false, '$2a$10$dummyhash', CURRENT_TIMESTAMP);
