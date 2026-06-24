-- Replace database-level ON DELETE CASCADE on T_organisation_accounts with JPA-level cascade.
-- Consistent with V01.035 approach: Hibernate Envers requires lifecycle events which DB cascades bypass.

-- T_organisation_accounts: org_id -> T_organisations
ALTER TABLE T_organisation_accounts DROP FOREIGN KEY FK_org_accounts_org;
ALTER TABLE T_organisation_accounts ADD CONSTRAINT FK_org_accounts_org
    FOREIGN KEY (org_id) REFERENCES T_organisations(id);
