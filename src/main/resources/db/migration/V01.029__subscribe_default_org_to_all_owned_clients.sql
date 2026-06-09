-- Subscribe the default org to every client it owns that is not yet subscribed.
-- This corrects the gap left by V01.024, which only seeded the abstratium-abstrauth subscription.
-- All clients migrated into the default org by V01.021 should have a subscription, because when clients are created, the creating org automatically subscribes to them.
INSERT INTO T_subscriptions (id, org_id, client_id, created_at)
SELECT UUID(), '${default_org_uuid}', client_id, CURRENT_TIMESTAMP
FROM T_oauth_clients
WHERE org_id = '${default_org_uuid}'
  AND client_id NOT IN (
      SELECT client_id FROM T_subscriptions WHERE org_id = '${default_org_uuid}'
  );
