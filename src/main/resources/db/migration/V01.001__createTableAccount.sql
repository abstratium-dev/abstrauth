CREATE TABLE T_accounts (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    email_verified BOOLEAN DEFAULT FALSE,
    name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    picture VARCHAR(500),
    auth_provider VARCHAR(50) DEFAULT 'native',
    CONSTRAINT I_accounts_email UNIQUE (email)
);
