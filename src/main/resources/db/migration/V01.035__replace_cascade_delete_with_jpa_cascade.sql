-- Replace database-level ON DELETE CASCADE with JPA-level cascade (CascadeType.REMOVE).
-- Hibernate Envers requires lifecycle events (@PreRemove/@PostRemove) which DB cascades bypass.
-- Transient tables (T_authorization_requests, T_authorization_codes, T_revoked_tokens) retain
-- their DB cascades as they carry no audit value.

-- T_credentials: account_id -> T_accounts
ALTER TABLE T_credentials DROP FOREIGN KEY FK_credentials_account_id;
ALTER TABLE T_credentials ADD CONSTRAINT FK_credentials_account_id
    FOREIGN KEY (account_id) REFERENCES T_accounts(id);

-- T_account_roles: account_id -> T_accounts
ALTER TABLE T_account_roles DROP FOREIGN KEY FK_account_roles_account;
ALTER TABLE T_account_roles ADD CONSTRAINT FK_account_roles_account
    FOREIGN KEY (account_id) REFERENCES T_accounts(id);

-- T_account_roles: client_id -> T_oauth_clients
ALTER TABLE T_account_roles DROP FOREIGN KEY FK_account_roles_client;
ALTER TABLE T_account_roles ADD CONSTRAINT FK_account_roles_client
    FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id);

-- T_federated_identities: account_id -> T_accounts
ALTER TABLE T_federated_identities DROP FOREIGN KEY FK_federated_account;
ALTER TABLE T_federated_identities ADD CONSTRAINT FK_federated_account
    FOREIGN KEY (account_id) REFERENCES T_accounts(id);

-- T_oauth_client_secrets: client_id -> T_oauth_clients
ALTER TABLE T_oauth_client_secrets DROP FOREIGN KEY FK_oauth_client_secrets_client;
ALTER TABLE T_oauth_client_secrets ADD CONSTRAINT FK_oauth_client_secrets_client
    FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id);

-- T_client_allowed_roles: client_id -> T_oauth_clients
ALTER TABLE T_client_allowed_roles DROP FOREIGN KEY FK_client_allowed_roles_client;
ALTER TABLE T_client_allowed_roles ADD CONSTRAINT FK_client_allowed_roles_client
    FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id);

-- T_client_roles: src_client_id -> T_oauth_clients
ALTER TABLE T_client_roles DROP FOREIGN KEY FK_client_roles_src_client;
ALTER TABLE T_client_roles ADD CONSTRAINT FK_client_roles_src_client
    FOREIGN KEY (src_client_id) REFERENCES T_oauth_clients(client_id);

-- T_client_roles: target_client_id -> T_oauth_clients
ALTER TABLE T_client_roles DROP FOREIGN KEY FK_client_roles_target_client;
ALTER TABLE T_client_roles ADD CONSTRAINT FK_client_roles_target_client
    FOREIGN KEY (target_client_id) REFERENCES T_oauth_clients(client_id);

-- T_subscriptions: org_id -> T_organisations
ALTER TABLE T_subscriptions DROP FOREIGN KEY FK_subscriptions_org;
ALTER TABLE T_subscriptions ADD CONSTRAINT FK_subscriptions_org
    FOREIGN KEY (org_id) REFERENCES T_organisations(id);

-- T_subscriptions: client_id -> T_oauth_clients
ALTER TABLE T_subscriptions DROP FOREIGN KEY FK_subscriptions_client;
ALTER TABLE T_subscriptions ADD CONSTRAINT FK_subscriptions_client
    FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id);
