-- Hibernate Envers audit tables for all auditable entities.
-- Transient tables (authorization_requests, authorization_codes, revoked_tokens)
-- are excluded as they carry no audit value.

-- T_accounts_AUD
CREATE TABLE T_accounts_AUD (
    id VARCHAR(36) NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    email VARCHAR(255),
    email_verified BOOLEAN,
    name VARCHAR(255),
    picture VARCHAR(500),
    auth_provider VARCHAR(50),
    created_at TIMESTAMP NULL,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_accounts_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_accounts_aud_rev ON T_accounts_AUD(REV);

-- T_credentials_AUD
CREATE TABLE T_credentials_AUD (
    id VARCHAR(36) NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    account_id VARCHAR(36),
    username VARCHAR(100),
    password_hash VARCHAR(255),
    failed_login_attempts INT,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP NULL,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_credentials_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_credentials_aud_rev ON T_credentials_AUD(REV);

-- T_oauth_clients_AUD
CREATE TABLE T_oauth_clients_AUD (
    id VARCHAR(36) NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    client_id VARCHAR(255),
    client_name VARCHAR(255),
    client_type VARCHAR(20),
    redirect_uris VARCHAR(5000),
    allowed_scopes VARCHAR(5000),
    require_pkce BOOLEAN,
    auto_subscribe BOOLEAN,
    publik BOOLEAN,
    created_at TIMESTAMP NULL,
    org_id VARCHAR(36),
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_oauth_clients_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_oauth_clients_aud_rev ON T_oauth_clients_AUD(REV);

-- T_account_roles_AUD
CREATE TABLE T_account_roles_AUD (
    id VARCHAR(36) NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    account_id VARCHAR(36),
    client_id VARCHAR(255),
    role VARCHAR(100),
    created_at TIMESTAMP NULL,
    org_id VARCHAR(36),
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_account_roles_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_account_roles_aud_rev ON T_account_roles_AUD(REV);

-- T_federated_identities_AUD
CREATE TABLE T_federated_identities_AUD (
    id VARCHAR(36) NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    account_id VARCHAR(36),
    provider VARCHAR(50),
    provider_user_id VARCHAR(255),
    email VARCHAR(255),
    connected_at TIMESTAMP NULL,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_federated_identities_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_federated_identities_aud_rev ON T_federated_identities_AUD(REV);

-- T_oauth_client_secrets_AUD
CREATE TABLE T_oauth_client_secrets_AUD (
    id BIGINT NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    client_id VARCHAR(255),
    secret_hash VARCHAR(255),
    created_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    is_active BOOLEAN,
    description VARCHAR(255),
    account_id VARCHAR(255),
    org_id VARCHAR(36),
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_oauth_client_secrets_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_oauth_client_secrets_aud_rev ON T_oauth_client_secrets_AUD(REV);

-- T_organisations_AUD
CREATE TABLE T_organisations_AUD (
    id VARCHAR(36) NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    name VARCHAR(255),
    created_by_account_id VARCHAR(36),
    created_at TIMESTAMP NULL,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_organisations_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_organisations_aud_rev ON T_organisations_AUD(REV);

-- T_organisation_accounts_AUD (composite PK entity)
CREATE TABLE T_organisation_accounts_AUD (
    org_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    added_at TIMESTAMP NULL,
    PRIMARY KEY (org_id, account_id, role, REV),
    CONSTRAINT FK_organisation_accounts_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_organisation_accounts_aud_rev ON T_organisation_accounts_AUD(REV);

-- T_subscriptions_AUD
CREATE TABLE T_subscriptions_AUD (
    id VARCHAR(36) NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    org_id VARCHAR(36),
    client_id VARCHAR(255),
    created_at TIMESTAMP NULL,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_subscriptions_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_subscriptions_aud_rev ON T_subscriptions_AUD(REV);

-- T_client_allowed_roles_AUD (composite PK entity)
CREATE TABLE T_client_allowed_roles_AUD (
    client_id VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    is_default BOOLEAN,
    available_to_foreign_orgs BOOLEAN,
    PRIMARY KEY (client_id, role, REV),
    CONSTRAINT FK_client_allowed_roles_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_client_allowed_roles_aud_rev ON T_client_allowed_roles_AUD(REV);

-- T_client_roles_AUD
CREATE TABLE T_client_roles_AUD (
    id VARCHAR(36) NOT NULL,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    role VARCHAR(100),
    org_id VARCHAR(36),
    src_client_id VARCHAR(255),
    target_client_id VARCHAR(255),
    created_at TIMESTAMP NULL,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_client_roles_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_client_roles_aud_rev ON T_client_roles_AUD(REV);
