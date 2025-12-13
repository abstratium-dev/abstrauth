-- Table for tracking revoked tokens
-- Used to prevent authorization code replay attacks and support token revocation

CREATE TABLE T_revoked_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token_jti VARCHAR(255) NOT NULL,
    revoked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(100) NOT NULL,
    authorization_code_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_revoked_tokens_authorization_code FOREIGN KEY (authorization_code_id) REFERENCES T_authorization_codes(id) ON DELETE CASCADE
);

-- FK indexes
CREATE INDEX idx_authorization_code_id ON T_revoked_tokens(authorization_code_id);

-- other indexes
CREATE INDEX idx_token_jti ON T_revoked_tokens(token_jti);
CREATE INDEX idx_revoked_at ON T_revoked_tokens(revoked_at);
