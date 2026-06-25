-- Add notification tracking columns to client secrets
-- Tracks when warning/expiration emails were sent to avoid duplicate notifications

ALTER TABLE T_oauth_client_secrets
  ADD COLUMN first_warning_sent_at TIMESTAMP NULL;

ALTER TABLE T_oauth_client_secrets
  ADD COLUMN final_warning_sent_at TIMESTAMP NULL;

ALTER TABLE T_oauth_client_secrets
  ADD COLUMN expired_notice_sent_at TIMESTAMP NULL;
