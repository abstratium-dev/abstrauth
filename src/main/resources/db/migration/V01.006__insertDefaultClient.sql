INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce)
VALUES (
    'e9877513-73cf-44fe-b581-4bad96e168cb',
    'abstratium-abstrauth',
    'abstratium abstrauth',
    'public',
    '["http://localhost:8080/api/auth/callback", "https://auth.abstratium.dev/auth-callback"]',
    '["openid", "profile", "email"]',
    TRUE
);
