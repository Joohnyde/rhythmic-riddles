# Database Scripts (root/db)

This folder contains the PostgreSQL scripts used to initialize the RhythmicRiddles/Cestereg database.

Execution order:
1. `db_00_create_db.sql` (template: `db_00_create_db.sql.example`) — create role + database and set privileges
2. `db_01_create_schema.sql` — base schema: tables, foreign keys, intrinsic constraints/indexes, extensions
3. `db_02_set_table_ownership.sql` — normalize ownership (excluding extension-owned objects)
4. `db_03_fill_tables_with_initial_data.sql` — baseline seed data (idempotent)
5. `db_04_add_runtime_invariants.sql` — additional runtime integrity rules introduced during foundation work

## Current script ownership

The scripts intentionally do not duplicate the same invariant in multiple files.

`db_01_create_schema.sql` owns the base schema. Rules that are intrinsic to the base identity/shape of a table belong there. In particular, `game.code` is unique in `db_01`; `db_04` must not create a second room-code uniqueness index/constraint.

`db_04_add_runtime_invariants.sql` owns the additional runtime invariants that were introduced separately during the current foundation work, such as:

- unique picked-category ordinals within one game;
- at most one unresolved team answer for one schedule while still allowing nested system interrupts;
- scalar game-state checks such as valid stage and positive configured limits.

Cross-room ownership remains an application invariant rather than a composite-FK schema design. Supported service write paths validate that externally supplied team/category/schedule/interrupt identifiers belong to the requested room.

## Pre-release versus released scripts

Until the first customer release, these scripts are still part of the evolving baseline and may be corrected in place when that produces the clearest current schema.

Once a script has shipped in the first customer release, treat it as frozen history: do not edit already-released SQL scripts to describe a later schema. A later database change should be introduced through a proper migration/versioning mechanism so an existing installation can move forward without losing data.

The current packaged embedded-database bootstrap is intentionally still a **run-once initializer**. It writes `.cestereg_sql_done` and skips SQL on later starts. Therefore a future packaged application version that requires a database change will need a migration mechanism before that upgrade can be supported safely. That work is deferred until after the first release; it is not implemented or tested by the current DB integration layer.

When upgrade support is introduced, evaluate a small plain-SQL migration tool rather than building a custom framework first. `Flyway Community` is the natural first candidate for the Java/Spring application because it tracks applied SQL migrations and checksums in a schema-history table. `dbmate` is a lightweight alternative if a standalone, framework-agnostic single binary is preferred. No tool choice is committed yet.

## Test bootstrap

Database integration tests intentionally initialize their ephemeral PostgreSQL database with:

1. `db_01_create_schema.sql`
2. `db_04_add_runtime_invariants.sql`

They do **not** execute `db_03_fill_tables_with_initial_data.sql`; tests seed only the rows required by each scenario through `QuizPersistenceFixture`. The packaged `psql` runner, first-run marker, and installer/update behavior remain release/package-test concerns.

⚠️ **Security:** `db_00_create_db.sql` contains credentials. Keep the repository version as a template (`change_me`) and inject real secrets through the release/deployment configuration.
