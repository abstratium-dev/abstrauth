-- Insert a default public client for testing
INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'abstrauth_admin_app',
    'abstrauth admin app',
    'public',
    '["http://localhost:8080/auth-callback", "http://localhost:4200/auth-callback", "https://auth.abstratium.dev/auth-callback"]',
    '["openid", "profile", "email"]',
    TRUE
);
