-- Remove the unused account_id column from T_oauth_client_secrets
ALTER TABLE T_oauth_client_secrets DROP FOREIGN KEY FK_oauth_client_secrets_account;
ALTER TABLE T_oauth_client_secrets DROP INDEX I_client_secrets_account;
ALTER TABLE T_oauth_client_secrets DROP COLUMN account_id;

-- Remove the corresponding audit column
ALTER TABLE T_oauth_client_secrets_AUD DROP COLUMN account_id;
