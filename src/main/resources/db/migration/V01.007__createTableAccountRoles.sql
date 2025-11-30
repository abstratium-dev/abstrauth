CREATE TABLE T_account_roles (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_account_roles_account FOREIGN KEY (account_id) REFERENCES T_accounts(id) ON DELETE CASCADE,
    CONSTRAINT I_account_roles_unique UNIQUE (account_id, client_id, role)
);

CREATE INDEX I_account_roles_account_client ON T_account_roles(account_id, client_id);
