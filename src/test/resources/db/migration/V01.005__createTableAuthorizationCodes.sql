CREATE TABLE authorization_codes (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(255) UNIQUE NOT NULL,
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
    FOREIGN KEY (authorization_request_id) REFERENCES authorization_requests(id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX idx_code ON authorization_codes(code);
CREATE INDEX idx_expires ON authorization_codes(expires_at);
