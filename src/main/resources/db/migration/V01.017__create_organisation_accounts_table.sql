CREATE TABLE T_organisation_accounts (
    org_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (org_id, account_id, role),
    CONSTRAINT FK_org_accounts_org FOREIGN KEY (org_id) REFERENCES T_organisations(id) ON DELETE CASCADE,
    CONSTRAINT FK_org_accounts_account FOREIGN KEY (account_id) REFERENCES T_accounts(id) ON DELETE CASCADE
);

-- FK indexes
CREATE INDEX I_org_accounts_org ON T_organisation_accounts(org_id);
CREATE INDEX I_org_accounts_account ON T_organisation_accounts(account_id);
