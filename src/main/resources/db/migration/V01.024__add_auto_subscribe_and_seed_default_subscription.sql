ALTER TABLE T_oauth_clients ADD COLUMN auto_subscribe BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE T_oauth_clients SET auto_subscribe=true WHERE client_id = 'abstratium-abstrauth';

-- Seed a subscription for the default org to the abstratium-abstrauth client
INSERT INTO T_subscriptions (id, org_id, client_id, created_at)
VALUES (
    UUID(),
    '${default_org_uuid}', 'abstratium-abstrauth', CURRENT_TIMESTAMP);

ALTER TABLE T_oauth_clients ADD COLUMN publik BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE T_oauth_clients SET publik=true WHERE client_id = 'abstratium-abstrauth';

