-- Insert a default public client for testing
INSERT INTO oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'test_spa_client',
    'Test SPA Client',
    'public',
    '["http://localhost:8080/callback", "http://localhost:3000/callback"]',
    '["openid", "profile", "email"]',
    TRUE
);
