# Database Scripts (root/db)

This folder contains the PostgreSQL scripts used to bootstrap the RhytmicRiddles/Cestereg database.

Execution order:
1. `db_00_create_db.sql` (template: `db_00_create_db.sql.example`) — create role + database, set privileges
2. `db_01_create_schema.sql` — schema (tables, constraints, indexes, extensions)
3. `db_02_set_table_ownership.sql` — normalize ownership (excludes extension-owned objects)
4. `db_03_fill_tables_with_initial_data.sql` — baseline seed data (idempotent)

⚠️ **Security:** `db_00_create_db.sql` contains credentials. Keep the repo version as a template (`change_me`) and inject real secrets via env/CI.

For full documentation, see `DATABASE.md` in the repository root.
