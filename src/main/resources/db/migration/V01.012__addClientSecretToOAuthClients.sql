-- Add client_secret_hash column for confidential client authentication
-- This allows backend servers to authenticate themselves when exchanging authorization codes

ALTER TABLE T_oauth_clients 
ADD COLUMN client_secret_hash VARCHAR(255) NULL;
