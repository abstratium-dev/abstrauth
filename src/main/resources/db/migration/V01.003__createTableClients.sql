CREATE TABLE T_oauth_clients (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL UNIQUE,
    client_name VARCHAR(255) NOT NULL,
    client_type VARCHAR(20) NOT NULL, -- 'public' or 'confidential'
    redirect_uris VARCHAR(5000) NOT NULL, -- JSON array of allowed redirect URIs
    allowed_scopes VARCHAR(5000), -- JSON array of allowed scopes
    require_pkce BOOLEAN DEFAULT TRUE,

    -- Add client_secret_hash column for confidential client authentication
    -- This allows backend servers to authenticate themselves when exchanging authorization codes
    client_secret_hash VARCHAR(255) NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
