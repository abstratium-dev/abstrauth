-- Migrate existing data into a default organisation
-- Creates organisation "rename-me" and links all existing data to it

-- Step 1: Create the default organisation
INSERT INTO T_organisations (id, name, created_by_account_id, created_at)
SELECT 
    '00000000-0000-0000-0000-000000000000' as id,
    'rename-me' as name,
    NULL as created_by_account_id,
    CURRENT_TIMESTAMP as created_at;

-- Step 2: Link all existing accounts as members of the default organisation
INSERT INTO T_organisation_accounts (org_id, account_id, role, added_at)
SELECT 
    '00000000-0000-0000-0000-000000000000' as org_id,
    id as account_id,
    'member' as role,
    CURRENT_TIMESTAMP as added_at
FROM T_accounts;

-- Step 3: Link accounts holding 'abstratium-abstrauth_admin' role as owners
-- These are accounts that have the admin role for the abstratium-abstrauth client
INSERT INTO T_organisation_accounts (org_id, account_id, role, added_at)
SELECT DISTINCT
    '00000000-0000-0000-0000-000000000000' as org_id,
    account_id,
    'owner' as role,
    CURRENT_TIMESTAMP as added_at
FROM T_account_roles
WHERE client_id = 'abstratium-abstrauth' 
  AND role = 'abstratium-abstrauth_admin';

-- Step 4: Set org_id on all scoped tables
UPDATE T_oauth_clients SET org_id = '00000000-0000-0000-0000-000000000000';
UPDATE T_account_roles SET org_id = '00000000-0000-0000-0000-000000000000';
UPDATE T_oauth_client_secrets SET org_id = '00000000-0000-0000-0000-000000000000';
UPDATE T_service_account_roles SET org_id = '00000000-0000-0000-0000-000000000000';
