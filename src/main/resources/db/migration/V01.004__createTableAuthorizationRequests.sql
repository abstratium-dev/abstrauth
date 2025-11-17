CREATE TABLE authorization_requests (
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
    INDEX idx_client_account (client_id, account_id),
    INDEX idx_status_expires (status, expires_at)
);
