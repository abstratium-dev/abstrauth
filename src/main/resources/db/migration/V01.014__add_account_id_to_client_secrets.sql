-- Add account_id column to track who created each secret
ALTER TABLE T_oauth_client_secrets 
  ADD COLUMN account_id VARCHAR(255);

-- Add foreign key constraint
ALTER TABLE T_oauth_client_secrets 
  ADD CONSTRAINT FK_oauth_client_secrets_account 
  FOREIGN KEY (account_id) REFERENCES T_accounts(id) ON DELETE SET NULL;

-- Create index for efficient lookups by account
CREATE INDEX I_client_secrets_account ON T_oauth_client_secrets (account_id);
