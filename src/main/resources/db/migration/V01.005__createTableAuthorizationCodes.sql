CREATE TABLE T_authorization_codes (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(255) NOT NULL,
    authorization_request_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    redirect_uri VARCHAR(500) NOT NULL,
    scope VARCHAR(500),
    code_challenge VARCHAR(255),
    code_challenge_method VARCHAR(10),
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_authorization_codes_authorization_request_id FOREIGN KEY (authorization_request_id) REFERENCES T_authorization_requests(id) ON DELETE CASCADE,
    CONSTRAINT FK_authorization_codes_account_id FOREIGN KEY (account_id) REFERENCES T_accounts(id) ON DELETE CASCADE,
    CONSTRAINT FK_authorization_codes_client_id FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id) ON DELETE CASCADE
);

-- FK indexes
CREATE INDEX I_authorization_codes_authorization_request_id ON T_authorization_codes(authorization_request_id);
CREATE INDEX I_authorization_codes_client_id ON T_authorization_codes(client_id);
CREATE INDEX I_authorization_codes_account_id ON T_authorization_codes(account_id);

-- other indexes
CREATE UNIQUE INDEX I_authorization_codes_code ON T_authorization_codes(code); -- for search
CREATE INDEX I_authorization_codes_expires_at ON T_authorization_codes(expires_at); -- for deletion
