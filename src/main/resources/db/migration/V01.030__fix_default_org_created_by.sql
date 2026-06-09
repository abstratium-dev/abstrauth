-- Fix created_by_account_id on the default organisation.
-- V01.021 queried role = 'admin' but the stored value is 'abstratium-abstrauth_admin',
-- so the column was always set to NULL. Back-fill it from T_account_roles.
UPDATE T_organisations
SET created_by_account_id = (
    SELECT account_id
    FROM T_account_roles
    WHERE client_id = 'abstratium-abstrauth'
      AND role = 'abstratium-abstrauth_admin'
    ORDER BY created_at ASC
    LIMIT 1
)
WHERE id = '${default_org_uuid}'
  AND created_by_account_id IS NULL;
