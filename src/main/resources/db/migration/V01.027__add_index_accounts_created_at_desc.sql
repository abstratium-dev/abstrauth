-- Add index to optimize ORDER BY created_at DESC queries
-- Used by findAccountsInOrg to avoid temp table materialization and filesort
CREATE INDEX I_accounts_created_desc ON T_accounts(created_at DESC, id);
