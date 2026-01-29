CREATE TABLE T_service_account_roles (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_service_account_roles_client FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id) ON DELETE CASCADE
);

-- FK indexes
CREATE INDEX I_service_account_roles_client ON T_service_account_roles(client_id);

-- other indexes
CREATE UNIQUE INDEX I_service_account_roles_unique ON T_service_account_roles(client_id, role);
