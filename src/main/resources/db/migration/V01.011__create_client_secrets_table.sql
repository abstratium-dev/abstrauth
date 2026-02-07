-- Create table for multiple client secrets per client
-- This enables zero-downtime secret rotation

CREATE TABLE T_oauth_client_secrets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(255),
    CONSTRAINT FK_oauth_client_secrets_client FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id) ON DELETE CASCADE
);

-- Create indexes separately for H2 compatibility
CREATE INDEX I_client_active ON T_oauth_client_secrets (client_id, is_active);
CREATE INDEX I_expires_at ON T_oauth_client_secrets (expires_at);

-- Migrate existing client secrets to new table
INSERT INTO T_oauth_client_secrets (client_id, secret_hash, is_active, description)
SELECT client_id, client_secret_hash, TRUE, 'Initial secret'
FROM T_oauth_clients 
WHERE client_secret_hash IS NOT NULL;
