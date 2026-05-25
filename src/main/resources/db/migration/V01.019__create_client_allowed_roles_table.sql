CREATE TABLE T_client_allowed_roles (
    client_id VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (client_id, role),
    CONSTRAINT FK_client_allowed_roles_client FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id) ON DELETE CASCADE
);

-- FK index
CREATE INDEX I_client_allowed_roles_client ON T_client_allowed_roles(client_id);
