CREATE TABLE T_organisations (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_by_account_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_organisations_created_by FOREIGN KEY (created_by_account_id) REFERENCES T_accounts(id) ON DELETE SET NULL
);

-- Index for created_by_account_id
CREATE INDEX I_organisations_created_by ON T_organisations(created_by_account_id);
