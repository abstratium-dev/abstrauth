-- Add auto_subscribe flag to OAuth clients (default true for backwards compatibility)
ALTER TABLE T_oauth_clients ADD COLUMN auto_subscribe BOOLEAN NOT NULL DEFAULT TRUE;

-- Seed a subscription for the default org to the abstratium-abstrauth client
INSERT INTO T_subscriptions (id, org_id, client_id, created_at)
VALUES (
    UUID(),
    '${default_org_uuid}', 'abstratium-abstrauth', CURRENT_TIMESTAMP);
