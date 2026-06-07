-- Fix the unique index on T_account_roles to include org_id
-- This allows the same account to have the same role for the same client
-- across different organizations (multitenancy requirement)

-- Drop the old unique index (does not include org_id)
DROP INDEX I_account_roles_unique ON T_account_roles;

-- Create new unique index that includes org_id
CREATE UNIQUE INDEX I_account_roles_unique ON T_account_roles(account_id, client_id, role, org_id);
