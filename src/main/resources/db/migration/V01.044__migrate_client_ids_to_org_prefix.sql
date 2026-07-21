-- Migrate existing OAuth client IDs to include the organisation prefix.
-- Before this migration, legacy client IDs were stored without the '<orgId>__' prefix.
-- This script prepends the default organisation UUID to every client ID that does not
-- already contain the double-underscore separator used to separate the org prefix.
-- Tables holding foreign-key references to T_oauth_clients.client_id are updated first,
-- then the primary table is updated. Foreign key checks are disabled for the duration of
-- the update because the client_id value changes in both parent and child tables.

SET FOREIGN_KEY_CHECKS = 0;

-- Helper condition: only unprefixed client IDs that are not the system abstratium-abstrauth client.
-- The abstratium-abstrauth client is excluded because its ID is hardcoded in Java role constants
-- (e.g. Roles.CLIENT_ID) and in the abstratium-abstrauth_<role> group names used in JWTs.
-- Update child tables that reference T_oauth_clients.client_id
UPDATE T_subscriptions
SET client_id = CONCAT('${default_org_uuid}', '__', client_id)
WHERE INSTR(client_id, '__') = 0
  AND client_id != 'abstratium-abstrauth';

UPDATE T_client_allowed_roles
SET client_id = CONCAT('${default_org_uuid}', '__', client_id)
WHERE INSTR(client_id, '__') = 0
  AND client_id != 'abstratium-abstrauth';

UPDATE T_client_roles
SET src_client_id = CONCAT('${default_org_uuid}', '__', src_client_id)
WHERE INSTR(src_client_id, '__') = 0
  AND src_client_id != 'abstratium-abstrauth';

UPDATE T_client_roles
SET target_client_id = CONCAT('${default_org_uuid}', '__', target_client_id)
WHERE INSTR(target_client_id, '__') = 0
  AND target_client_id != 'abstratium-abstrauth';

UPDATE T_oauth_client_secrets
SET client_id = CONCAT('${default_org_uuid}', '__', client_id)
WHERE INSTR(client_id, '__') = 0
  AND client_id != 'abstratium-abstrauth';

UPDATE T_account_roles
SET client_id = CONCAT('${default_org_uuid}', '__', client_id)
WHERE INSTR(client_id, '__') = 0
  AND client_id != 'abstratium-abstrauth';

UPDATE T_authorization_requests
SET client_id = CONCAT('${default_org_uuid}', '__', client_id)
WHERE INSTR(client_id, '__') = 0
  AND client_id != 'abstratium-abstrauth';

UPDATE T_authorization_codes
SET client_id = CONCAT('${default_org_uuid}', '__', client_id)
WHERE INSTR(client_id, '__') = 0
  AND client_id != 'abstratium-abstrauth';

-- Update the primary client table last
UPDATE T_oauth_clients
SET client_id = CONCAT('${default_org_uuid}', '__', client_id)
WHERE INSTR(client_id, '__') = 0
  AND client_id != 'abstratium-abstrauth';

SET FOREIGN_KEY_CHECKS = 1;
