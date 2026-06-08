-- Add indexes to optimize read-heavy queries
-- These indexes support the application's read-heavy workload

-- Index for OrganisationService.listOrganisationsForAccount subquery
-- Optimizes: SELECT oa.id.orgId FROM OrganisationAccount oa WHERE oa.id.accountId = :accountId AND oa.id.role = :role
CREATE INDEX I_org_accounts_account_role ON T_organisation_accounts(account_id, role, org_id);

-- Index for ClientSecretService.findActiveSecrets
-- Optimizes: SELECT cs FROM ClientSecret cs WHERE cs.clientId = :clientId AND cs.active = true AND (cs.expiresAt IS NULL OR cs.expiresAt > :now)
CREATE INDEX I_client_secrets_lookup ON T_oauth_client_secrets(client_id, is_active, expires_at);

-- Index for AccountRole queries by account and client
-- Optimizes: SELECT ar FROM AccountRole ar WHERE ar.accountId = :accountId AND ar.clientId = :clientId
-- And: SELECT ar FROM AccountRole ar WHERE ar.accountId = :accountId
CREATE INDEX I_account_roles_account_client ON T_account_roles(account_id, client_id, role);

-- Index for OrganisationAccount lookups by org (used in findAccountsInOrg)
-- Optimizes: SELECT oa FROM OrganisationAccount oa WHERE oa.id.orgId = :orgId AND oa.id.role = 'member'
CREATE INDEX I_org_accounts_org_role ON T_organisation_accounts(org_id, role, account_id);
