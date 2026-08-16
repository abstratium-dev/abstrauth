-- Replace the boolean is_default column on T_client_allowed_roles with a
-- VARCHAR default_assignment column holding one of three enum string values:
--   'not_default'       — not auto-seeded (was is_default = false)
--   'all_users'         — seeded for every member on first sign-in (was is_default = true)
--   'org_owners_only'   — seeded only when the signing-in user is an org owner
--
-- The same change is applied to the Envers audit table T_client_allowed_roles_AUD,
-- which also has an is_default column (created in V01.037). That past migration
-- cannot be edited (Flyway checksum validation), so both tables are migrated here.

-- Step 1: Add the new column as nullable so existing rows can be backfilled.
ALTER TABLE T_client_allowed_roles
    ADD COLUMN default_assignment VARCHAR(30);

-- Step 2: Backfill from is_default.
UPDATE T_client_allowed_roles SET default_assignment = 'all_users' WHERE is_default = TRUE;
UPDATE T_client_allowed_roles SET default_assignment = 'not_default' WHERE is_default = FALSE OR is_default IS NULL;

-- Step 3: Make the column NOT NULL now that every row has a value.
-- Use MODIFY COLUMN (MySQL syntax) which H2 in MODE=MySQL also supports.
-- ALTER COLUMN ... SET NOT NULL is H2-only and fails on MySQL.
ALTER TABLE T_client_allowed_roles
    MODIFY COLUMN default_assignment VARCHAR(30) NOT NULL;

-- Step 4: Add a CHECK constraint to enforce valid enum values at the database level.
-- This is defense-in-depth: the JPA layer (DefaultAssignmentConverter) already
-- prevents invalid values from being persisted by the application, but this
-- constraint also protects against raw SQL, manual data entry, and bugs.
-- Both MySQL 9.3 (CHECK enforced since 8.0.16) and H2 (MODE=MySQL) support this.
ALTER TABLE T_client_allowed_roles
    ADD CONSTRAINT CK_client_allowed_roles_default_assignment
    CHECK (default_assignment IN ('not_default', 'all_users', 'org_owners_only'));

-- Step 5: Drop the old column.
ALTER TABLE T_client_allowed_roles
    DROP COLUMN is_default;

-- Step 6: Update the Envers audit table to match.
ALTER TABLE T_client_allowed_roles_AUD
    ADD COLUMN default_assignment VARCHAR(30);

UPDATE T_client_allowed_roles_AUD SET default_assignment = 'all_users' WHERE is_default = TRUE;
UPDATE T_client_allowed_roles_AUD SET default_assignment = 'not_default'
    WHERE is_default = FALSE OR is_default IS NULL;

ALTER TABLE T_client_allowed_roles_AUD
    MODIFY COLUMN default_assignment VARCHAR(30) NOT NULL;

-- The audit table also gets a CHECK constraint for consistency.
ALTER TABLE T_client_allowed_roles_AUD
    ADD CONSTRAINT CK_client_allowed_roles_aud_default_assignment
    CHECK (default_assignment IN ('not_default', 'all_users', 'org_owners_only'));

ALTER TABLE T_client_allowed_roles_AUD
    DROP COLUMN is_default;
