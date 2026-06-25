-- Add notification tracking columns to the client secrets audit table
-- so Envers can record changes to these fields.

ALTER TABLE T_oauth_client_secrets_AUD
  ADD COLUMN first_warning_sent_at TIMESTAMP NULL;

ALTER TABLE T_oauth_client_secrets_AUD
  ADD COLUMN final_warning_sent_at TIMESTAMP NULL;

ALTER TABLE T_oauth_client_secrets_AUD
  ADD COLUMN expired_notice_sent_at TIMESTAMP NULL;
