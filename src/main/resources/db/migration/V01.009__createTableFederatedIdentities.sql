CREATE TABLE T_federated_identities (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT I_federated_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT FK_federated_account FOREIGN KEY (account_id) REFERENCES T_accounts(id) ON DELETE CASCADE
);

CREATE INDEX I_federated_account_id ON T_federated_identities(account_id);
