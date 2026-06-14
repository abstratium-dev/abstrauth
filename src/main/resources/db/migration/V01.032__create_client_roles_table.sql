-- Table for client-to-client role assignments.
-- Allows a source client to be assigned roles for calling a target client.
-- The role must exist in T_client_allowed_roles for the target client.
CREATE TABLE T_client_roles (
    id VARCHAR(36) PRIMARY KEY,
    role VARCHAR(100) NOT NULL,
    org_id VARCHAR(36) NOT NULL,
    src_client_id VARCHAR(255) NOT NULL,
    target_client_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_client_roles_src_client FOREIGN KEY (src_client_id) REFERENCES T_oauth_clients(client_id) ON DELETE CASCADE,
    CONSTRAINT FK_client_roles_target_client FOREIGN KEY (target_client_id) REFERENCES T_oauth_clients(client_id) ON DELETE CASCADE
);

-- FK indexes
CREATE INDEX I_client_roles_src_client ON T_client_roles(src_client_id);
CREATE INDEX I_client_roles_target_client ON T_client_roles(target_client_id);

-- Composite unique index to prevent duplicate role assignments
CREATE UNIQUE INDEX I_client_roles_unique ON T_client_roles(src_client_id, target_client_id, role);

-- Index for org-based queries (Hibernate tenant filter)
CREATE INDEX I_client_roles_org ON T_client_roles(org_id);
