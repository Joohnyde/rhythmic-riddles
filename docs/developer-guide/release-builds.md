
# Release Builds and Desktop Packaging

This document explains how **release mode** works for RhythmicRiddles (Cestereg), how to build production artifacts on each platform, and how embedded PostgreSQL + assets + frontend are bundled so end users can **click and run** without installing developer tooling.

## Goals and non-goals

### Goals

- Produce **self-contained** builds that require **no system Java**.
- Support two runtime modes:
  - **Embedded PostgreSQL** (default for “end-user machines”)
  - **External PostgreSQL** (for environments that already provide Postgres)
- Bundle **frontend** into the Spring Boot jar.
- Bundle **assets** into the packaged application image.
- Bind services to **localhost** only.
- Provide at least:
  - Linux **portable app-image** and **.deb**
  - Windows **portable app-image** and **.msi**
  - macOS **portable app-image** and **.dmg** (currently built on Intel macOS; Apple Silicon users can run the Intel build via Rosetta until a native Apple Silicon builder is available)

### Non-goals

- Internet-facing server deployment.
- Docker-based distribution to end users.
- Cross-compiling installers for other OSes from a single machine (jpackage is platform-specific).

## Release mode: profiles and runtime modes

Release mode is driven by **Spring profiles** and a build-time property.

### `production` profile (runtime behavior)

The `production` Spring profile enables “desktop UX” behavior and production web behavior, including:

- **Bind server to localhost** (`127.0.0.1`) via `application-production.yml`.
- **Actuator** bound to localhost on a dedicated port and exposes only limited endpoints.
- **Browser auto-launch** on app start (`BrowserLauncher`, production-only).
- **SPA routing support** (`ProductionSpaRoutingConfig`, production-only) so deep links in the Angular app work when served from Spring Boot.

### `embeddb` profile (runtime behavior)

The `embeddb` Spring profile enables embedded PostgreSQL:

- Starts Postgres programmatically (Zonky Embedded Postgres).
- Uses OS-specific persistent application data folders for DB state.
- Runs SQL bootstrap scripts once on first run.
- Shuts down Postgres cleanly on app exit.

### `-Dembeddb=false` (build-time behavior)

The build supports an “external DB packaging” mode:

- When building with `-Dembeddb=false`, the Maven `external-db` profile activates and **strips the bundled pg-client** output from the build output, so external builds don’t accidentally ship Postgres client tooling.

**Important distinction**

- `-Dembeddb=false` affects what gets included in the packaged jar/resources.
- `--spring.profiles.active=production` vs `production,embeddb` controls runtime behavior.

## Repository responsibilities (what gets shipped)

A release build is composed of:

1. A **jpackage application image** (bundles a Java runtime + launchers)
2. **Assets folder** (shipped as a runtime payload)
3. OS installer artifact (.deb, .exe)
4. Optional: **embedded Postgres binaries + pg-client** (when embeddb mode is intended)

## Frontend bundling: Angular into the backend jar

When building with `-Pproduction`, the Maven build runs the Angular build and packages it into the backend jar.

### Build steps (production profile)

The production Maven profile performs these steps:

1. Installs Node + npm for a reproducible build (via `frontend-maven-plugin`)
2. Runs `npm ci`
3. Runs the Angular production build
4. Copies Angular output into the backend jar under:
   - `target/classes/static`

This is what makes the backend jar self-contained (it serves the SPA directly).

### Why SPA routing config is required

Single Page Applications handle routing client-side. If a user refreshes a deep link (e.g., `/admin/settings`), Spring Boot would normally return **404** unless that path maps to a server route.

`ProductionSpaRoutingConfig` fixes this by forwarding requests without file extensions (no `.` in the path) back to `/index.html`, letting Angular handle routing.

## SQL patches and idempotency

### Which SQL scripts are shipped in production builds

When `-Dspring.profiles.active=embeddb` is set, the build **copies** a set of SQL scripts from a repository-level directory (`../../db`) into:

- `target/classes/db`

This ensures the packaged jar always contains the intended “release SQL script set”.

### How scripts execute in embedded mode

In embedded mode, scripts are executed via `psql` (not via JDBC), which allows using `psql` features such as `\connect` and `\i`.

Execution model (`PsqlScriptRunner`):

- All scripts under `classpath:db/*.sql` are extracted to a temp folder.
- Scripts are sorted by filename to ensure deterministic ordering.
- Script 0 is treated as an **admin phase**:
  - connects as user `postgres` to DB `postgres`
  - intended for tasks like creating the app DB/user
- Remaining scripts are treated as an **app phase**:
  - connects as the configured app user to the target database
  - intended for schema + data setup

### Run-once marker

Embedded bootstrap writes a marker file to prevent re-running SQL on every startup:

- `${appData}/postgres/data/.cestereg_sql_done`

This means:

- On first run: scripts execute.
- On subsequent runs: scripts are skipped (for speed and safety).

### Why idempotency still matters

Even with a marker file, scripts should be written to be safe in repeated or partial-run scenarios:

- The app could crash mid-bootstrap.
- A developer might delete the marker intentionally.
- Scripts may be manually rerun during maintenance.

Best practice:

- `CREATE ... IF NOT EXISTS` (where available)
- constraint creation guarded
- `INSERT ... ON CONFLICT DO NOTHING` for seed data
- versioned scripts with monotonic naming (`db_00_...`, `db_01_...`, etc.)

## Embedded database architecture (why it exists and how it works)

### Why embedded DB is needed

End users cannot be expected to have:

- PostgreSQL installed
- a DBA skill set
- the ability to provision users/databases and run migrations

Embedded mode ships a complete local DB stack:

- Embedded server (Zonky Embedded Postgres)
- Bundled `psql` client + libs to run SQL scripts reliably
- Persistent on-disk DB state

### Where embedded DB state is stored

Embedded Postgres uses OS-specific application data directories via `AppDataDirs`:

- Windows: `%LOCALAPPDATA%\cestereg`
- macOS: `~/Library/Application Support/cestereg`
- Linux: `$XDG_DATA_HOME/cestereg` or `~/.local/share/cestereg`

Within that base directory, embedded Postgres uses:

- `<base>/postgres/dist` (runtime unpack/working area)
- `<base>/postgres/data` (persistent data directory)
- `<base>/postgres/sql` (reserved directory created by config)

The embedded Postgres configuration explicitly sets:

- `setCleanDataDirectory(false)` so DB state persists across restarts.

### How embedded DB is disabled

To run against an external PostgreSQL, do not enable the `embeddb` profile:

- Runtime:
  - `--spring.profiles.active=production`
- Build packaging:
  - `mvn -Pproduction -Dembeddb=false clean package`

To build a release without an embedded DB you run a builder script with an added parameter `--embeddb=false` .

Spring datasource properties from `application-production.yml` still apply.

## Platform selection: `-Dplatform=linux|windows|macos`

The `-Dplatform` property controls which embedded Postgres binary dependency is included at runtime.

Profiles:

- `-Dplatform=linux` → adds Linux embedded Postgres binaries (runtime dependency)
- `-Dplatform=windows` → adds Windows embedded Postgres binaries
- `-Dplatform=macos` → adds macOS embedded Postgres binaries

This prevents shipping cross-platform binary bloat.

It also controls which pg-client packer runs (Linux/Windows/macOS packer executions are skipped by default and enabled only for the selected platform).

## Bundled `psql` client: what it is and why it’s separate

### Why we ship a bundled `psql`

`PsqlScriptRunner` executes SQL files using `psql`, because `psql` supports script features that are awkward or impossible to replicate reliably via JDBC.

To avoid requiring system PostgreSQL tools, the app ships a minimal “pg-client bundle” in resources.

### How it’s packaged into the jar

The jar includes a tarball at:

- `/pg-client/<platform>/pg-client.tgz`

At runtime, `PgClientBundler`:

- extracts this tarball into a writable directory under app data:
  - `<appData>/pg-client/<platform>/v18/...`
- uses a marker file `.installed` to avoid extracting every startup
- ensures executables are marked executable (non-Windows)

### How the tarball is created (builder-side)

The tarball is created during the production build by running platform-specific packer scripts:

- Linux: `postgres_packer.sh`
- Windows: `postgres_packer.ps1`
- macOS: `postgres_packer_macos.sh`

**Important**: these packer scripts run on the _build machine_, and require a real `psql` installation there.

## Packaging with jpackage

jpackage is used to produce:

- **Portable app-image** (folder that can be run directly)
- **OS installer artifacts** (`.deb`, `.msi`,`.dmg`)

The produced app-image bundles:

- a minimal Java runtime (no user Java install)
- your Spring Boot jar
- launchers (`bin/<app>` on Linux, `<app>.exe` on Windows, `.app` launcher bundle on macOS)

### Runtime arguments passed by the packagers

Linux, Windows, and macOS scripts pass:

- `--spring.profiles.active=production` or `production,embeddb`
- `--app.assets.base-dir=$APPDIR/resources/data`

`$APPDIR` is a jpackage runtime placeholder that resolves to the application directory.

This is the mechanism that makes the packaged app locate assets correctly without relying on the current working directory.

## Release payload: assets and zip layout

All build scripts expect a repository-level directory:

- `data/`

During packaging, this directory is copied into the app-image under:

- Linux app-image: `<app>/lib/app/resources/data`
- Windows app-image: placed next to the jar directory as `<jarDir>/resources/data`
- macOS app-image: placed next to the jar/config inside the app bundle as `/Contents/app/resources/data`

This “resources/data” layout matches the `--app.assets.base-dir=$APPDIR/resources/data` runtime argument.

If you distribute as a zip:

- zip the resulting `dist/<platform>/out/<app>` directory (portable app-image)
- ensure the `resources/data` folder is included

## Building on Linux

The Linux builder script is:

- `build/build_linux_jpackage.sh`

### Prerequisites (builder machine)

- JDK with `jpackage` available (jpackage is part of modern JDK distributions)
- Maven available
- System Postgres client tooling to provide `psql` during pg-client bundling:
  - the script expects a real psql at `/usr/lib/postgresql/18/bin/psql`
- `dpkg-deb` since we want `.deb` output

### Build commands

Embedded build (default):

```bash
bash build/build_linux_jpackage.sh --embeddb=true
```

External DB build:

```bash
bash build/build_linux_jpackage.sh --embeddb=false
```

### Outputs

- Portable app-image:
  - `dist/linux/out/cestereg/`
- `.deb`:
- `dist/linux/out/*.deb` (only if `dpkg-deb` is available)

### Smoke test

Run:

```bash
dist/linux/out/cestereg/bin/cestereg
```

## Building on Windows

The Windows builder script is:

- `build/build_windows_jpackage.ps1`

### Prerequisites (builder machine)

On Windows, the packaging scripts rely on a few external tools being available on `PATH` (or in standard install locations). During setup, you may need to add these directories to your **System PATH** so `jpackage`, `mvn`, and related build utilities can be discovered:

- `C:\Program Files\Java\jdk-25.0.2\bin` (JDK tools, including `jpackage`)
- `C:\Program Files (x86)\apache-maven\apache-maven-3.9.13\bin` (Maven CLI)
- `C:\Program Files (x86)\WiX Toolset v3.14\bin` (WiX tools for `.msi` generation)
- `C:\Program Files (x86)\dumpbin` (needed by some Windows packaging/build flows to inspect binaries)

Helpful download sources (matching one working builder setup):

- Apache [Maven 3.9.13](https://dlcdn.apache.org/maven/maven-3/3.9.13/binaries/apache-maven-3.9.13-bin.tar.gz)
- Oracle [JDK 25](https://download.oracle.com/java/25/latest/jdk-25_windows-x64_bin.exe) (Windows x64) with `jpackage` available
- WiX Toolset (required for `jpackage --type msi`)
  - install via `winget install WiXToolset.WiXToolset` (PowerShell Admin)
  - restart the shell and verify `candle.exe` / `light.exe` are on `PATH`.
    > **Note:**
    > winget will install the newest version. Make sure it's correct in PATH
- [PostgreSQL 18.3](https://sbp.enterprisedb.com/getfile.jsp?fileid=1260041) (for `psql.exe`, used by the pg-client packer)
  - the packer first tries `psql.exe` from PATH
  - fallback: `C:\Program Files\PostgreSQL\18\bin\psql.exe`
- [Git](https://github.com/git-for-windows/git/releases/download/v2.53.0.windows.1/Git-2.53.0-64-bit.exe) for Windows (recommended for build scripting + repo tooling)
- [dumpbin](https://github.com/Delphier/dumpbin/releases/download/v14.50.35722/dumpbin-14.50.35722-x64.zip) (standalone)

### Build commands

Embedded build (default):

```powershell
powershell -ExecutionPolicy Bypass -File build\build_windows_jpackage.ps1 -embeddb "true"
```

External DB build:

```powershell
powershell -ExecutionPolicy Bypass -File build\build_windows_jpackage.ps1 -embeddb "false"
```

### Outputs

- Portable `.exe` app-image:
  - `dist\windows\out\cestereg\`
- `.msi`:
  - created in `dist\windows\out\` if WiX is installed and the MSI build succeeds

### Smoke test

Run:

- `dist\windows\out\cestereg\cestereg.exe`

### MSI installer notes

The Windows script can build both `.exe` and `.msi` installers from the same app-image. If WiX is missing, `jpackage` will still build the portable app-image but **installer** generation will fail.
After installing WiX, verify that `candle.exe` and `light.exe` are available from a fresh PowerShell session before re-running the build script.

### Post-build cleanup behavior

The Windows script removes:

- backend `target/`
- frontend `dist/`

This keeps the repo clean after creating the release artifacts.

## Building on macOS

The macOS builder script is:

- `build/build_macos_jpackage.sh`

### Builder status and supported architectur

The current macOS packaging flow is implemented and validated on **Intel macOS** (`x86_64`). The current packaged pg-client bundle is also Intel-only (`macos-x86_64`). Apple Silicon users can run the Intel build through **Rosetta** until a native Apple Silicon builder is available.

### Prerequisites (builder machine)

A working macOS builder should have the following installed:

- macOS at least **Ventura 13.5.x** (this is the currently validated builder environment)
- Oracle [JDK 25](https://download.oracle.com/java/25/latest/jdk-25_macos-x64_bin.dmg) with `jpackage` available
- Apache [Maven 3.9.13](https://dlcdn.apache.org/maven/maven-3/3.9.13/binaries/apache-maven-3.9.13-bin.tar.gz) available
- Node.js 24.11.1 available:

```bash
curl  -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.4/install.sh | bash
source ~/.zshrc
nvm install 24.11.1
```

- [PostgreSQL 18](https://sbp.enterprisedb.com/getfile.jsp?fileid=1260055) installed locally so the macOS pg-client packer can copy real binaries from `/Library/PostgreSQL/18`
- Xcode Command Line Tools available installed with `xcode-select --install`

> **Note:**
> Prefer Homebrew when available; the steps above show a manual install that works if tools end up on `PATH`.

### Build command

Embedded build (default):

```bash
bash build/build_macos_jpackage.sh --embeddb=true
```

External DB build:

```bash
bash build/build_macos_jpackage.sh --embeddb=false
```

### Output

- Portable app-image:
- `dist/macos/out/cestereg.app`
- `.dmg`
- `dist/macos/out/*.dmg`

### Smoke test

Run either:

```bash
open dist/macos/out/cestereg.app
```

or, for terminal logs:

```bash
dist/macos/out/cestereg.app/Contents/MacOS/cestereg
```

To test the installer path:

```bash
open dist/macos/out/*.dmg
```

### macOS payload layout

The macOS build copies the shared `data/` payload into the app bundle next to the packaged jar/config under:

- `cestereg.app/Contents/app/resources/data`
  This matches the runtime argument:
- `--app.assets.base-dir=$APPDIR/resources/data`

### Intel vs Apple Silicon / Rosetta

The current macOS release artifacts are built as **Intel (`x86_64`)** packages.
This is acceptable for now because Apple Silicon Macs can run Intel macOS applications through **Rosetta**.
This includes the Intel Java launcher and the bundled Intel PostgreSQL client payload, as long as the bundle is internally consistent and all shipped macOS binaries are Intel builds.

### What to do once an Apple Silicon builder is available

When an Apple Silicon macOS build machine becomes available, the temporary Intel-only workaround should be replaced with a native Apple Silicon path. The expected follow-up work is:

- enable the native Apple Silicon embedded Postgres dependency in `pom.xml`
- enable the matching Apple Silicon macOS packer/build-script wirin
- produce a `macos-arm64` pg-client bundle in addition to the current `macos-x86_64` one
- verify embedded db + assets end-to-end on Apple Silicon without Rosetta
- optionally move later to a universal macOS packaging approach if both architectures need to be distributed from the same release workflow

## Troubleshooting guide

### App starts but frontend routes 404 on refresh

Ensure:

- runtime includes `--spring.profiles.active=production`
- `ProductionSpaRoutingConfig` is active (production profile)

### Embedded DB does not run / data resets unexpectedly

Check:

- `embeddb` profile is active
- app has write access to the OS app data directory
- `.cestereg_sql_done` marker exists under the embedded Postgres data folder (after first successful bootstrap)

### SQL bootstrap fails

Common causes:

- a non-idempotent script failing on repeated run
- missing scripts in classpath `db/*.sql`
- pg-client extraction failure (missing platform bundle in resources)

### Assets not found

Ensure the packaged app was launched with:

- `--app.assets.base-dir=$APPDIR/resources/data`

And verify `resources/data` exists inside the app-image.

