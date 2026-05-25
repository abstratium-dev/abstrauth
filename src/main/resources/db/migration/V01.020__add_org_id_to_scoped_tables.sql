-- Add org_id column to organisation-scoped tables
-- These columns will be made non-nullable after data migration

-- T_oauth_clients
ALTER TABLE T_oauth_clients ADD COLUMN org_id VARCHAR(36);
CREATE INDEX I_oauth_clients_org_id ON T_oauth_clients(org_id);
ALTER TABLE T_oauth_clients ADD CONSTRAINT FK_oauth_clients_org FOREIGN KEY (org_id) REFERENCES T_organisations(id);

-- T_account_roles
ALTER TABLE T_account_roles ADD COLUMN org_id VARCHAR(36);
CREATE INDEX I_account_roles_org_id ON T_account_roles(org_id);
ALTER TABLE T_account_roles ADD CONSTRAINT FK_account_roles_org FOREIGN KEY (org_id) REFERENCES T_organisations(id);

-- T_oauth_client_secrets
ALTER TABLE T_oauth_client_secrets ADD COLUMN org_id VARCHAR(36);
CREATE INDEX I_client_secrets_org_id ON T_oauth_client_secrets(org_id);
ALTER TABLE T_oauth_client_secrets ADD CONSTRAINT FK_client_secrets_org FOREIGN KEY (org_id) REFERENCES T_organisations(id);

-- T_service_account_roles
ALTER TABLE T_service_account_roles ADD COLUMN org_id VARCHAR(36);
CREATE INDEX I_service_account_roles_org_id ON T_service_account_roles(org_id);
ALTER TABLE T_service_account_roles ADD CONSTRAINT FK_service_account_roles_org FOREIGN KEY (org_id) REFERENCES T_organisations(id);
