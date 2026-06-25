-- Remove the unused created_by_account_id column from T_organisations
ALTER TABLE T_organisations DROP FOREIGN KEY FK_organisations_created_by;
ALTER TABLE T_organisations DROP INDEX I_organisations_created_by;
ALTER TABLE T_organisations DROP COLUMN created_by_account_id;

-- Remove the corresponding audit column
ALTER TABLE T_organisations_AUD DROP COLUMN created_by_account_id;
