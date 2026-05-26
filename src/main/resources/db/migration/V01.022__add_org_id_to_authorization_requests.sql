-- Add org_id to T_authorization_requests to carry the selected organisation through the sign-in flow
ALTER TABLE T_authorization_requests ADD COLUMN org_id VARCHAR(36) NULL;
