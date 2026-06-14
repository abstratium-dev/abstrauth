ALTER TABLE T_client_allowed_roles
    ADD COLUMN available_to_foreign_orgs BOOLEAN NOT NULL DEFAULT TRUE;

-- Existing ClientAllowedRole rows were created when the table only represented
-- roles available to subscribing (foreign) organisations, so default to TRUE.
-- Remove the default constraint so future inserts must explicitly specify the value.
ALTER TABLE T_client_allowed_roles
    ALTER COLUMN available_to_foreign_orgs DROP DEFAULT;
