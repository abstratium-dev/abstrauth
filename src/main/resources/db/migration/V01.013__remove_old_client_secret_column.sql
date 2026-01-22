-- Remove the old client_secret_hash column from T_oauth_clients
-- This is safe because secrets are now in T_oauth_client_secrets table

ALTER TABLE T_oauth_clients DROP COLUMN client_secret_hash;
