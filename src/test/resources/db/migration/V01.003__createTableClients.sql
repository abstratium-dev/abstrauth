CREATE TABLE oauth_clients (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) UNIQUE NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    client_type VARCHAR(20) NOT NULL, -- 'public' or 'confidential'
    redirect_uris VARCHAR(5000) NOT NULL, -- JSON array of allowed redirect URIs
    allowed_scopes VARCHAR(5000), -- JSON array of allowed scopes
    require_pkce BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_client_id ON oauth_clients(client_id);
