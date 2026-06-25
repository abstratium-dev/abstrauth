-- Replace database-level ON DELETE CASCADE on T_organisation_accounts (account_id -> T_accounts) with JPA-level cascade.
-- Consistent with V01.035 and V01.038 approach: Hibernate Envers requires lifecycle events which DB cascades bypass.

-- T_organisation_accounts: account_id -> T_accounts
ALTER TABLE T_organisation_accounts DROP FOREIGN KEY FK_org_accounts_account;
ALTER TABLE T_organisation_accounts ADD CONSTRAINT FK_org_accounts_account
    FOREIGN KEY (account_id) REFERENCES T_accounts(id);
