-- Add auth_method column to track which authentication method was used for this login session
ALTER TABLE T_authorization_requests ADD COLUMN auth_method VARCHAR(20);
