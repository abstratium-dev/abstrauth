CREATE TABLE T_subscriptions (
    id VARCHAR(36) PRIMARY KEY,
    org_id VARCHAR(36) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_subscriptions_org FOREIGN KEY (org_id) REFERENCES T_organisations(id) ON DELETE CASCADE,
    CONSTRAINT FK_subscriptions_client FOREIGN KEY (client_id) REFERENCES T_oauth_clients(client_id) ON DELETE CASCADE
);

-- FK indexes
CREATE INDEX I_subscriptions_org ON T_subscriptions(org_id);
CREATE INDEX I_subscriptions_client ON T_subscriptions(client_id);

-- Unique constraint: one subscription per org/client combination
CREATE UNIQUE INDEX I_subscriptions_unique ON T_subscriptions(org_id, client_id);
