
# Database Guide

This document describes **everything database-related** for the project: local development setup, embedded vs external DB, schema overview (ERD), tables/columns, SQL scripts, idempotency rules, seeding/dumps, and how scripts are executed automatically in the devcontainer.


## Quick start

### Option A — Devcontainer (recommended)

The dev environment uses Docker Compose to run Postgres and mounts `db/` into Postgres init directory. On first start, Postgres runs the scripts automatically.

**Start:**

```bash
docker compose -f docker-compose.yml up -d
```

**Connect:**

```bash
psql "postgresql://rhytmicriddles:change_me@127.0.0.1:2345/rhytmicriddles"
```

### Option B — Local Postgres install (no Docker)

Install Postgres locally and run scripts in order with `psql` (details below).


## Database modes: External vs Embedded

The application supports two runtime database modes.

### External Postgres (system-installed)

- You run Postgres yourself (Docker, system service, managed instance, etc.).
- App connects via `spring.datasource.url/username/password`.
- Recommended for local dev and for “operator-managed DB” installations.

### Embedded Postgres (bundled inside the app)

- App ships its own embedded Postgres server and a bundled `psql` client.
- Used for fully standalone distributions (no external dependencies).
- The app starts/stops Postgres with the application lifecycle.

**Key implication:** the **same SQL scripts** in `db/` must be compatible with:

- docker entrypoint init (`/docker-entrypoint-initdb.d`)
- bundled/embedded `psql` execution (because scripts use `\connect`, etc.)


## Installing Postgres (External DB)

### Linux (Debian/Ubuntu/Mint)

- Recommended: use your distro packages.
- Typical packages:
    - `postgresql`
    - `postgresql-client`

Example:

```bash
sudo apt update
sudo apt upgrade
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/postgresql.gpg
sudo apt update
sudo apt -y install postgresql-18
```

### macOS

- Common approach: Homebrew

```bash
brew install postgresql@18
brew services start postgresql@18
```

### Windows

- Install using the [official Postgres installer](https://sbp.enterprisedb.com/getfile.jsp?fileid=1260041).
- Ensure `psql` is available in PATH.


## Schema overview (ERD)

### Mermaid ERD

```mermaid
erDiagram
  GAME ||--o{ TEAM : has
  ALBUM ||--o{ TRACK : contains
  SONG  ||--o{ TRACK : referenced_by

  GAME  ||--o{ CATEGORY : has
  ALBUM ||--o{ CATEGORY : used_in
  TEAM  ||--o{ CATEGORY : picked_by

  CATEGORY ||--o{ SCHEDULE : schedules
  TRACK    ||--o{ SCHEDULE : plays

  SCHEDULE ||--o{ INTERRUPT : receives
  TEAM     ||--o{ INTERRUPT : triggers

  ALBUM {
    uuid id PK
    varchar name
    varchar custom_question
  }

  GAME {
    uuid id PK
    timestamp date
    int stage
    int max_songs
    int max_albums
    varchar(4) code
    varchar(128) password_hash
  }

  SONG {
    uuid id PK
    varchar authors
    varchar name
    double snippet_duration
    double answer_duration
  }

  TEAM {
    uuid id PK
    text button_code
    uuid game_id FK
    varchar name
    varchar image
  }

  TRACK {
    uuid id PK
    uuid album_id FK
    uuid song_id FK
    varchar custom_answer
  }

  CATEGORY {
    uuid id PK
    uuid album_id FK
    uuid game_id FK
    uuid picked_by_team_id FK
    int ordinal_number
    bool is_done
  }

  SCHEDULE {
    uuid id PK
    int ordinal_number
    uuid track_id FK
    timestamp started_at
    timestamp revealed_at
    uuid category_id FK
  }

  INTERRUPT {
    uuid id PK
    uuid schedule_id FK
    timestamp arrived_at
    timestamp resolved_at
    bool is_correct
    int score_or_scenario_id
    uuid team_id FK
  }


```

## Tables and columns

Below is a practical description of each table (purpose + columns). For exact DDL, see `db_01_create_schema.sql`.

### `album`

**Purpose:** Groups songs into themed sets/categories (“albums” in this game’s domain).

Columns:

- `id (uuid, PK)` — application-assigned UUID
- `name (varchar, required)` — display name
- `custom_question (varchar, optional)` — optional per-album question override. If null the question is "Guess the song"

### `song`

**Purpose:** Song metadata (audio stored as assets, not in DB).

Columns:

- `id (uuid, PK)`
- `authors (varchar, required)`
- `name (varchar, required)`
- `snippet_duration (double, required)` — seconds
- `answer_duration (double, required)` — seconds

### `track`

**Purpose:** Links a song to an album; allows album-specific answer customization.

Columns:

- `id (uuid, PK)`
- `album_id (uuid, FK → album.id)`
- `song_id (uuid, FK → song.id)`
- `custom_answer (varchar, optional)` — optional per-track question override. If null the question is 'SONG_AUTHORS - SONG_NAME'

### `game`

**Purpose:** A game session.

Columns:

- `id (uuid, PK)`
- `date (timestamp, required)`
- `stage (int, required, default 0)` — game state/stage machine
- `max_songs (int, required, default 10)`
- `max_albums (int, required, default 10)`
- `code (varchar(4), required)` — join code
- `password_hash (varchar(128), optional)` — optional admin/operator password (TODO)

Indexes:

- `index_game_code` on `code` (with `deduplicate_items=true`)

### `team`

**Purpose:** Teams/players within a game.

Columns:

- `id (uuid, PK)`
- `button_code (text, optional)` — external buzzer/button identifier
- `game_id (uuid, FK → game.id)`
- `name (varchar, required)`
- `image (varchar, optional)` — Team specific icon, ideally AI generated before the quiz. URL or asset path

Index:

- `idx_team_button_code`

### `category`

**Purpose:** An album presented inside a particular game; tracks whether picked/complete.

Columns:

- `id (uuid, PK)`
- `album_id (uuid, FK → album.id, required)`
- `game_id (uuid, FK → game.id, required)`
- `picked_by_team_id (uuid, FK → team.id, optional)` — which team picked it. 'Null' means it was picked by an admin
- `ordinal_number (int, optional)` — Order in which albums were picked. 'Null' means unpicked
- `is_done (boolean, optional)` — completion state

### `schedule`

**Purpose:** Concrete plan of which track plays when (per category), including timestamps.

**Note:** Table is fully-filled when a team chooses a category. MAX_SONGS number of tracks are being chosen, preferring ones that weren't already heard.

Columns:

- `id (uuid, PK)`
- `ordinal_number (int, required)` — ordering
- `track_id (uuid, FK → track.id, required)`
- `started_at (timestamp, optional)`
- `revealed_at (timestamp, optional)`
- `category_id (uuid, FK → category.id, required)`

### `interrupt`

**Purpose:** Captures (buzzer and system) interrupts during playback.

Columns:

- `id (uuid, PK)`
- `schedule_id (uuid, FK → schedule.id, required)` — which playback was interrupted
- `arrived_at (timestamp, required)`
- `resolved_at (timestamp, optional)`
- `is_correct (boolean, optional)`
- `score_or_scenario_id (int, optional)` — post-answer score for buzzer interrupts; pre-interrupt UI scenario ID for system interrupts.
- `team_id (uuid, FK → team.id, optional)` — team who buzzed in, null if it's a system interrupt

Indexes:

- `idx_interrupt_schedule_arrived_time (schedule_id, arrived_at desc)`
- `idx_interrupt_team_schedule (team_id, schedule_id)`
- `interrupt_index_arrived_desc (arrived_at desc, deduplicate_items=true)`


## SQL scripts in `db/`

All scripts are **idempotent** (safe to run multiple times) and designed to work in:

- Postgres Docker entrypoint init
- embedded/packaged execution via `psql`

### Script order (important)

1.  `db_00_create_db.sql` (template provided as `db_00_create_db.sql.example`)
2.  `db_01_create_schema.sql`
3.  `db_02_set_table_ownership.sql`
4.  `db_03_fill_tables_with_initial_data.sql`

#### `db_00_create_db.sql` (bootstrap: role + database)

**Purpose:**

- creates role/user (if missing)
- sets password
- creates the database (if missing)
- connects to the new DB
- sets schema/database privileges and default privileges

**How idempotency works:**

- Uses `SELECT format(...) WHERE NOT EXISTS (...) \gexec` so the CREATE only runs when missing.

**Security warning (sensitive):**

- This script contains credentials via:
    
    ```sql
    \set db_pass ...
    ```
    
- **Never commit real production passwords.**
    
- Keep the repo version as a template with `change_me`, and inject real credentials via:
    
    - environment variables (Docker) ←**preferred**
    - CI secrets
    - `.env` file ignored by git
    - or `psql -v db_pass=...`

Recommended pattern:

- Keep `db_00_create_db.sql.example` in the repo
- In local/devcontainer:
    - Use docker environment variables
- In production:
    - generate a concrete `db_00_create_db.sql` during build/deploy

#### `db_01_create_schema.sql` (schema)

**Purpose:**

- creates tables
- adds PK/FK constraints
- adds indexes

**How idempotency works:**

- `CREATE TABLE IF NOT EXISTS`
    
- PK/FK blocks are guarded via:
    
    ```sql
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '...')
    ```
    
- indexes use `CREATE INDEX IF NOT EXISTS`
    

#### `db_02_set_table_ownership.sql` (ownership normalization)

**Purpose:**

- Ensures objects in `public` are owned by the **current database owner**.

**Key technique:**

- Determine `db_owner`:
    
    ```sql
    SELECT r.rolname ... WHERE d.datname = current_database();
    ```
    
- Alter ownership for tables/sequences/views/functions
    
- Excludes extension members using `pg_depend dep.deptype = 'e'`
    

**Why it exists:**

- In different environments, schema objects might be created by different users (postgres vs app role).
- Ownership must be normalized so that later migrations/operations don’t fail.
- Usually only a problem on linux

#### `db_03_fill_tables_with_initial_data.sql` (baseline seed)

**Purpose:**

- Inserts initial/baseline data (albums, games, teams, categories, songs, tracks).
- Leaves runtime tables (`schedule`, `interrupt`) empty.

**How idempotency works:**

- Uses `ON CONFLICT (id) DO NOTHING` for every insert block.

**Important ordering:**  
To avoid inserting a child before its parent (FK violations), this script inserts in dependency order:

1.  `album` (parent)
2.  `game` (parent)
3.  `team` (depends on game)
4.  `category` (depends on album + game + optionally team)
5.  `song` (parent)
6.  `track` (depends on album + song)

`schedule` depends on `category` and `track`, and `interrupt` depends on `schedule` and `team`, so they are not seeded here.


## Running scripts manually (external Postgres)

### Using a single psql session (recommended)

If your scripts include `\connect`, you must use `psql` (not JDBC migration tools that don’t support psql meta-commands).

```bash
psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -f db_00_create_db.sql
psql -h 127.0.0.1 -p 5432 -U rhytmic_riddles -d rhytmic_riddles -f db_01_create_schema.sql
psql -h 127.0.0.1 -p 5432 -U rhytmic_riddles -d rhytmic_riddles -f db_02_set_table_ownership.sql
psql -h 127.0.0.1 -p 5432 -U rhytmic_riddles -d rhytmic_riddles -f db_03_fill_tables_with_initial_data.sql
```

### Passing secrets safely (avoid hardcoding)

Example:

```bash
psql -h 127.0.0.1 -p 5432 -U postgres -d postgres \
  -v db_name='rhytmic_riddles' \
  -v db_user='rhytmic_riddles' \
  -v db_pass="$DB_PASS" \
  -f db_00_create_db.sql
```


## Devcontainer automation (Docker Compose)

The provided `docker-compose.yml.example` runs:

- `db` service: `postgres:18.2`
    
- volume mount:
    
    ```yaml
    - ./db/:/docker-entrypoint-initdb.d:ro
    ```
    

### How Postgres init scripts work

Postgres Docker image runs scripts in `/docker-entrypoint-initdb.d` **only when the database directory is empty** (first initialization of the volume).

In this repo, the persistent volume is:

- `cestereg_pgdata`

So:

- first run: scripts execute automatically
- subsequent runs: scripts do **not** rerun automatically (because the DB is already initialized)

**Why scripts still must be idempotent:**

- embedded mode may run them differently
- teammates may re-run scripts manually
- CI / test envs may rebuild volumes
- you want safe retries

> Devcontainer details (how we attach IDE, run services, etc.) will be documented in a separate devcontainer document later. This DB guide assumes docker-compose is the entry point.


## Idempotency requirements (non-negotiable)

All scripts should be safe to re-run without destroying data.

Recommended patterns (already used in this repo):

### Create objects

- Tables:
    
    ```sql
    CREATE TABLE IF NOT EXISTS ...
    ```
    
- Extensions:
    
    ```sql
    CREATE EXTENSION IF NOT EXISTS ...
    ```
    
- Indexes:
    
    ```sql
    CREATE INDEX IF NOT EXISTS ...
    ```
    

### Constraints (PK/FK)

Postgres does not support `ADD CONSTRAINT IF NOT EXISTS`, so guard with `DO $$`:

```sql
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='my_constraint') THEN
    ALTER TABLE ...
      ADD CONSTRAINT my_constraint ...
  END IF;
END $$;
```

### Seed data

Use:

```sql
INSERT ... ON CONFLICT (id) DO NOTHING;
```

### Avoid destructive operations

Do **not**:

- `DROP TABLE`
- `TRUNCATE`
- `DELETE FROM ...` without very strict guards

If you need changes over time, add dedicated **patch scripts** (e.g. `db_patch_2026_02_01_11_52_add_column.sql`) and track their execution.


## References

- Source of truth for schema: `db/db_01_create_schema.sql`
- Seed data: `db/db_03_fill_tables_with_initial_data.sql`
- Dev env automation: `docker-compose.yml` (`/docker-entrypoint-initdb.d` mounting)
