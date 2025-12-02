ALTER TABLE T_accounts ADD COLUMN picture VARCHAR(500);
ALTER TABLE T_accounts ADD COLUMN auth_provider VARCHAR(50) DEFAULT 'native';
