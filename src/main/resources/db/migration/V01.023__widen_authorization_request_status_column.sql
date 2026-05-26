-- Widen the status column to accommodate 'org_selection_pending' (21 chars)
ALTER TABLE T_authorization_requests ALTER COLUMN status VARCHAR(25) NOT NULL;
