---
trigger: glob
globs: **/*.sql
---

The database follows strict naming conventions for consistency and clarity:

- **Tables**: Prefixed with `T_` (e.g., `T_accounts`, `T_oauth_clients`)
- **Foreign Keys**: Format `FK_<tableName>_<columnName>` (e.g., `FK_credentials_account_id`)
- **Indices**: Format `I_<tableName>_<columnName(s)>` (e.g., `I_accounts_email`)
- **Primary Keys**: Always named `id` using VARCHAR(36) for UUID storage
- **Timestamps**: Use `created_at` and `expires_at` naming pattern

SQL files must support MySQL (production) and H2 (testing).

You may read the database using something like the following docker command: `docker run -it --rm --network abstratium mysql mysql -h abstratium-mysql --port 3306 -u abstrauth -psecret` but DO NOT CREATE, DELETE or UPDATE data without asking first! this is primarly meant for you to read the data if it helps during debugging.
