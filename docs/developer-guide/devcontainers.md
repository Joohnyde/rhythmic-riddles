

# Dev Environment and Devcontainers

This document explains how the project’s **containerized dev environment** works (Dev Containers + Docker Compose), how it differs from a fully local setup, and how to run the system in both modes.

It also documents the repo’s helper scripts, database bootstrapping behavior, and IDE integration (VS Code, IntelliJ, NetBeans).


## What this setup is trying to achieve

The goal of the devcontainer approach is to provide a **consistent toolchain** across contributors:

- same Linux base
- same Java / Maven versions
- same Node / npm / Angular CLI versions
- same Postgres version and initialization behavior

This reduces “works on my machine” issues and makes onboarding easier.

## Requirements

To start the devcontainer or containerized dev environment you need:

- Docker installed
- Docker daemon running
- Docker Compose v2 available via `docker compose`
- Enough disk space for container images + the Postgres volume

The repository includes a “doctor” script that checks these prerequisites and verifies the dev environment is healthy.


## Installing Docker

### Windows
Install **Docker Desktop for Windows** following [Docker’s official guide](https://docs.docker.com/desktop/setup/install/windows-install/).

Notes:
- Docker Desktop uses WSL2 on Windows (recommended default).
- After installation, ensure Docker Desktop is running before using `docker compose`.

### macOS
Install [**Docker Desktop for Mac**](https://docs.docker.com/desktop/setup/install/mac-install/) (Apple Silicon vs Intel builds)

### Linux (Ubuntu/Debian)
Recommended: install Docker Engine using Docker’s [official Ubuntu guide](https://docs.docker.com/engine/install/ubuntu/).

Docker Compose:
- Compose is commonly available as part of Docker Desktop, or as a [Docker Engine plugin](https://docs.docker.com/compose/install/linux/#install-using-the-repository).



## High-level architecture

This environment is driven by:

- **Docker Compose**: defines services (database + dev toolbox container)
- **Dev Containers**: configures how VS Code attaches to that toolbox container
- **Helper scripts**: one-command up/down/doctor and backend runner

### Conceptual difference:  `env-*`  scripts vs  `run-*`  scripts
The helper scripts are split into two conceptual groups.

**env-* scripts**

-   Manage the  **container environment lifecycle**
-   Start or stop the dev infrastructure (database + dev toolbox container)

Examples:

-   `scripts/dev/env-up.sh`
-   `scripts/dev/env-down.sh`

These scripts ensure the required containers exist and are running.

**run-* scripts**

-   Start  **application processes inside the dev container**
-   They rely on the environment provided by the  `dev`  container

Examples:

-   `scripts/dev/run-backend.sh`
-   `scripts/dev/run-frontend.sh`

This separation allows developers to either:

1.  Start the environment once and manually run commands inside the container
2.  Use the run-scripts as quick shortcuts that ensure the environment is up and start the application

### Services

- `db`: Postgres database
- `dev`: a toolbox container where you run Maven/Node/Angular commands

Key idea:
- The `dev` container “owns” your build toolchain.
- Your IDE can either attach to the `dev` container, or you can run IDE locally while executing commands inside `dev`.



## Repository files (what they do)

### `.devcontainer/devcontainer.json`
This is the Dev Containers entrypoint configuration.

Key behaviors:
- Uses the repo root `docker-compose.yml` and attaches to service `dev`
- Starts both services automatically: `db` and `dev`
- Sets workspace folder inside container: `/workspace`
- Installs VS Code extensions (EditorConfig, Prettier, ESLint, Java pack, Spring Boot, Angular)
- Forwards ports: **4200** (Angular) and **8080** (Spring Boot)

Important fields:
- `service: "dev"` → the container VS Code attaches to
- `dockerComposeFile: ["../docker-compose.yml"]` → compose file is stored at repo root
- `forwardPorts: [4200, 8080]` → browser/IDE can access services via localhost ports

### `.devcontainer/Dockerfile`
Defines the toolbox container image for `dev`.

What it installs:
- Base image: Ubuntu 24.04 (Dev Containers base)
- Java (Temurin JDK 25) installed under `/opt/java`
- Maven 3.9.6 installed under `/opt/maven` and linked into PATH
- Node 24.11.1 and npm 11.9.0
- Angular CLI 21.0.1 (global)
- PostgreSQL client (`psql`) for debugging

Why it exists:
- Contributors don’t need to install Java/Node toolchains locally
- Everyone runs builds/tests with consistent versions

### `docker-compose.yml.example`
Template compose file that illustrates the environment layout.

It defines:
- `db` (Postgres 18.2) exposing **host port 2345 → container port 5432**
- `dev` (toolbox) mounting the repo into `/workspace` and staying alive (`sleep infinity`)

**Credentials handling in the example**
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` are shown as **MOCK values**
- In a real setup, credentials should come from:
  - `.env` file ignored by git, or
  - environment variables, or
  - your local secret manager workflow

Also note:
- The file is named `docker-compose.yml.example` to avoid committing “real” credentials by default.
- Developers typically copy it to `docker-compose.yml` and adjust locally.

### `scripts/dev/env-up.sh`
Starts the dev environment (`db + dev`).

What it does:
- Validates Docker exists
- Validates Docker daemon is running
- Ensures `docker-compose.yml` exists in repo root
- Runs:
  ```bash
  docker compose up -d --build db dev
  ```
- Prints connection info:
  - DB on host: `localhost:2345`
  - DB inside docker network: `db:5432`

### `scripts/dev/env-down.sh`
Stops and removes containers but **keeps volumes** (so DB data persists).

Command used:
```bash
docker compose down
```

Important:
- This intentionally preserves `cestereg_pgdata` volume so your DB state persists between runs.

### `scripts/dev/doctor.sh`
Health-check tool for dev environment.

It validates:
- Docker + Docker daemon
- Docker Compose v2 availability
- Required project files exist
- Compose parses correctly and has `db` and `dev` services
- Services start successfully
- **Docker DNS/network works** (from `dev`, resolve `db`)
- Postgres readiness (`pg_isready -h db -p 5432` from inside `dev`)
- Toolchain exists inside `dev` (java/mvn/node/npm/ng/psql)
- Best-effort verification that DB has objects created

It also includes **informational checks** for:
- NetBeans action files (docker actions)
- VS Code devcontainers extension presence
- IntelliJ launcher presence (best-effort)

This script is the fastest way to verify a new developer machine is set up correctly.

### `scripts/dev/run-backend.sh`
One-command backend runner inside the dev environment.

What it does:
- Ensures `db` and `dev` are up:
  ```bash
  docker compose up -d db dev
  ```
- Executes Maven inside the `dev` container:
  ```bash
  docker exec cestereg-dev bash -lc "cd apps/backend && mvn spring-boot:run"
  ```

### scripts/dev/run-frontend.sh
Runs the frontend inside the dev container.
What it does:
- Ensures `db` and `dev` are up:
  ```bash
  docker compose up -d db dev
  ```
- Installs dependencies and runs the app:
  ```bash
  npm install --prefer-offline
  npm start
  ```

## Database bootstrapping and persistence

### Where DB data is stored
Postgres data is stored in a named Docker volume:
- `cestereg_pgdata`

In the compose template, it’s mounted into:
- `/var/lib/postgresql` (inside the container)

This volume is **persistent**:
- `docker compose down` does not delete it
- Your DB state survives restarts

To fully wipe the DB, you would remove the volume explicitly:
```bash
docker volume rm cestereg_pgdata
```

### How schema/data is created initially
DB init scripts are mounted into:
- `/docker-entrypoint-initdb.d`

In the compose template:
```yaml
- ./db/:/docker-entrypoint-initdb.d:ro
```

Postgres behavior:
- Scripts in `./db` run **only on first initialization** of the volume.
- If the volume already contains a DB cluster, scripts will not re-run automatically.

This is why the SQL scripts are designed to be **idempotent** (safe to rerun manually).


## Networking: `db` vs `localhost`

This is one of the key “gotchas” when switching between host and container execution.

### Inside Docker network (devcontainer / toolbox container)
Containers communicate by service name (Docker DNS):

- host: `db`
- port: `5432`

So your backend config (when backend runs inside `dev`) should use:
```
jdbc:postgresql://db:5432/...
```

### From your laptop (host OS)
Your laptop talks to the Postgres container via published ports:

- host: `127.0.0.1` or `localhost`
- port: `2345` (mapped to container 5432)

So your backend config (when backend runs on host) should use:
```
jdbc:postgresql://127.0.0.1:2345/...
```


## Running the project

### Option A — Fully containerized dev workflow (recommended)
You run builds and services inside the `dev` container.

1) Run backend:
```bash
./scripts/dev/run-backend.sh
```

2) Run frontend:
```bash
./scripts/dev/run-frontend.sh
```

Ports:
- Backend: http://localhost:8080
- Frontend: http://localhost:4200

### Option B — Manual dev container workflow

Alternatively you can work directly inside the dev container.

1) Start environment:
```bash
./scripts/dev/env-up.sh
```

2) Enter dev container:
```
docker compose exec dev bash
```

3) Run backend:
```
cd apps/backend
mvn spring-boot:run
```

4) Run frontend:
```
cd apps/frontend
npm start
```

5) Stop environment:
```
./scripts/dev/env-down.sh
```

### Option C — Hybrid workflow (DB in Docker, backend/frontend on host)
You keep Postgres containerized, but run code with your local toolchain.

1) Start only DB:
```bash
docker compose up -d db
```

2) Configure backend datasource to use:
- host: `127.0.0.1`
- port: `2345`

3) Run backend locally:
```bash
mvn spring-boot:run
```

4) Run frontend locally:
```bash
npm install
npm start
```

This is useful if you prefer native debugging and already have toolchains installed.


## IDE integration

### VS Code (best integration)
VS Code has first-class Dev Containers support.

Workflow:
1) Install “Dev Containers” extension (`ms-vscode-remote.remote-containers`)
2) Open repo
3) “Reopen in Container”
4) VS Code attaches to the `dev` service, and your terminal/builds happen inside it

Ports 8080/4200 are forwarded automatically via devcontainer.json.

### IntelliJ IDEA: Open and Run the Project in a Dev Container

IntelliJ IDEA can start a Dev Container **directly from the IDE** 
JetBrains’ flow is based on opening a project that contains `devcontainer.json`, then creating a container and connecting to it via **JetBrains Client**.

#### Prerequisites
- Docker installed and running
- Your repository contains a Dev Container config:
  - typically `.devcontainer/devcontainer.json`
  - (or an equivalent directory that includes `devcontainer.json`)

#### Step-by-step (Mount Sources workflow)
This is the recommended workflow for day-to-day development because it keeps your repository on disk and mounts it into the container.

1) **Open the project** in IntelliJ IDEA (the one that contains the `devcontainer.json`).  
2) Open the file **`devcontainer.json`** in the editor.  
3) In the **left gutter** (beside the editor), click the **Create Dev Container** icon.
4) Choose: **Create Dev Container and Mount Sources**.
5) Watch the build in the **Services** tool window (container build + startup).  
6) When it finishes, click **Connect**.
7) The project opens in **JetBrains Client**, running *inside the container backend*.

Reference: JetBrains guide “Start Dev Container inside IDE”.  
https://www.jetbrains.com/help/idea/start-dev-container-inside-ide.html

#### Running and debugging
Once connected, treat JetBrains Client as the “in-container IDE”:

- Run Maven / Spring Boot from the IDE run configurations (they execute in-container).
- Use the integrated terminal to run scripts and commands (also in-container).

#### Quick note: executing commands inside the dev container
From JetBrains Client terminal (recommended) or any terminal on your host:

```bash
docker compose exec dev bash
```

This opens a shell inside the container at `/workspace`, where you can run:

```bash
mvn -v
mvn -pl apps/backend spring-boot:run
ng serve --host 0.0.0.0
psql -h db -p 5432 -U <user> -d <db>
```


### NetBeans: “Run” Button Starts Backend via Dev Containers (Docker profile)


This guide explains how to create a NetBeans configuration called **`docker`** that routes the Run action through `nbactions-docker.xml`.

#### What this achieves
- NetBeans remains your editor.
- The backend actually runs inside the `dev` container (consistent Java/Maven).
- The container orchestration + boot is handled by a repo script (e.g., `scripts/dev/run-backend.sh`).

#### Prerequisites
- Docker installed and running
- `docker compose` works on the machine
- Repo scripts exist and are executable (especially `scripts/dev/run-backend.sh`)
- NetBeans is using a Maven project

#### Step 1 — Create/Update the shared NetBeans configuration file

In the **backend module directory** (the Maven project NetBeans opens), create or update this file:

`nb-configuration.xml`

Add a `docker` configuration entry like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project-shared-configuration>
  <config-data xmlns="http://www.netbeans.org/ns/maven-config-data/1">
    <configurations>
      <configuration id="docker" profiles=""/>
    </configurations>
  </config-data>
</project-shared-configuration>
```

Notes:
- The `id="docker"` is the name that appears in NetBeans as a configuration.
- `profiles=""` is intentionally empty here because the Run behavior is implemented via actions (next step).

#### Step 2 — Create `nbactions-docker.xml` (the action wiring)

In the **same backend module directory**, create:

`nbactions-docker.xml`

With this content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<actions>
  <action>
    <actionName>run</actionName>
    <packagings>
      <packaging>jar</packaging>
    </packagings>
    <goals>
      <goal>org.codehaus.mojo:exec-maven-plugin:3.1.0:exec</goal>
    </goals>
    <properties>
      <exec.executable> bash</exec.executable>
      <exec.args> ../../scripts/dev/run-backend.sh</exec.args>
    </properties>
  </action>
</actions>
```

What this does:
- Overrides the **Run** action for the `docker` configuration
- Uses Maven’s `exec-maven-plugin` to execute `bash`
- Calls the repo script `../../scripts/dev/run-backend.sh` (path is relative to the backend module)

#### Step 3 — Ensure the script path is correct and executable

Confirm the script exists relative to the backend module:

- backend module directory → `../../scripts/dev/run-backend.sh`

On Linux/macOS, ensure it is executable:

```bash
chmod +x scripts/dev/run-backend.sh
```

#### Step 4 — Select the configuration in NetBeans

1) Open the backend project in NetBeans.
2) In the toolbar, locate the **configuration selector** (often shows “Default”).
3) Select the configuration **`docker`**.

Now when you press the **green Run button**, NetBeans uses `nbactions-docker.xml` and runs the backend via the container script.

#### Step 5 — What the developer should experience

- Press **Run**
- Docker Compose starts `db` and `dev` (if not already running)
- The backend runs inside the `dev` container
- Ports (8080/4200) behave the same as in Dev Container usage

This keeps the workflow transparent while still enforcing consistent toolchains.


## Local vs Devcontainer: what’s different?

### Devcontainer mode
- Toolchain is inside container (Java/Maven/Node/Angular)
- DB hostname is `db`
- Files are edited via container-mounted workspace
- More consistent across contributors

### Local mode
- Toolchain is installed on your machine
- DB hostname is `localhost` / `127.0.0.1` and port is mapped
- Better native debugging, but more “machine drift” risk


## Suggested conventions for contributors

- Use `./scripts/dev/doctor.sh` as the first step when onboarding or troubleshooting.
- Prefer devcontainer for “clean” reproducibility.
- Avoid committing real DB credentials — use `.env` or local env vars.
- If you change folder layout (e.g., backend path), keep scripts and doctor checks in sync.

