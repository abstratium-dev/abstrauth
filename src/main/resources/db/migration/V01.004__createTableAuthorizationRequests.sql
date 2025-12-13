CREATE TABLE T_authorization_requests (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    account_id VARCHAR(36) NULL,
    redirect_uri VARCHAR(500) NOT NULL,
    scope VARCHAR(500),
    state VARCHAR(255),
    code_challenge VARCHAR(255),
    code_challenge_method VARCHAR(10),
    status VARCHAR(20) NOT NULL, -- 'pending', 'approved', 'denied', 'expired'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    auth_method VARCHAR(20),
    CONSTRAINT FK_authorization_requests_account_id FOREIGN KEY (account_id) REFERENCES T_accounts(id) ON DELETE CASCADE,
    CONSTRAINT FK_authorization_requests_client_id FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id) ON DELETE CASCADE
);

-- FK indexes
CREATE INDEX I_authorization_requests_account_id ON T_authorization_requests(account_id);
CREATE INDEX I_authorization_requests_client_id ON T_authorization_requests(client_id);

-- other indexes
CREATE INDEX I_authorization_requests_status_expires_at ON T_authorization_requests(status, expires_at); -- for deletion
