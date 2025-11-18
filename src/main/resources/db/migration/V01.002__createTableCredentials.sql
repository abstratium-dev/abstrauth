CREATE TABLE T_credentials (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    failed_login_attempts INT DEFAULT 0,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_credentials_account_id FOREIGN KEY (account_id) REFERENCES T_accounts(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX I_credentials_username ON T_credentials(username);
