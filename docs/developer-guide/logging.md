
# Logging

This document defines the logging approach for RhythmicRiddles (Cestereg): **what we log, where logs go, how they roll**, and the conventions we follow so logs stay useful in development and production.


## Goals

- **Debuggability:** understand game flow, state reconstruction, and failures from logs alone.
- **Operational clarity:** distinguish expected lifecycle events from real incidents.
- **Low noise:** avoid flooding logs with high-frequency events (especially WebSockets).
- **Security & privacy:** never leak secrets, tokens, credentials, or sensitive content.
- **Consistency:** same formatting, levels, and key fields across the codebase.

## Current stack

We use the standard Spring Boot logging stack:

- **SLF4J** API (`org.slf4j.Logger` / `LoggerFactory`)
- **Logback** implementation (Spring Boot default)
- **Rolling file logging** (size/time based) plus console output where appropriate



## Where logs go

### Development (local / devcontainer)
- Console output is the primary signal (IDE terminal / container logs).
- File logging is enabled and they go into `apps/backend/logs` folder.

### Production (desktop packaged builds)
- Logs must be written to a **user-writable** directory (never requires admin/root).
- Logs should be easy to locate for support (document the exact folder in the release docs).

**Policy:** logs must never be written to the application installation directory (may be read-only on installed builds).


## Log rotation & retention

We use rolling logs to prevent unbounded growth:

- Rotate by **day** and/or **max file size**
- Keep a defined number of historical files (retention)
- Optionally compress old logs (`.gz`) to save disk space

**Recommendation (desktop friendly defaults):**
- max file size: 10–50 MB
- retention: 14–30 days
- keep total cap (optional): e.g., 500 MB


## Levels and when to use them

Use levels consistently. If in doubt, prefer **INFO** for major lifecycle, **DEBUG** for details, **WARN** for recoverable issues, **ERROR** for failures requiring attention.

- **TRACE:** extremely noisy low-level detail (normally off).
- **DEBUG:** detailed diagnostic info (state computation, branch decisions).
- **INFO:** expected lifecycle events (startup, stage changes, key user actions).
- **WARN:** unexpected but recoverable situations (timeouts, retries, missing optional assets).
- **ERROR:** operation failed (request cannot complete, data inconsistent, startup failure).

### Examples that should be INFO
- Game created / room created
- Stage transition (lobby → albums → songs → winner)
- Category picked / song advanced / reveal triggered
- Successful embedded DB startup / asset extraction complete

### Examples that should be WARN
- WebSocket reconnects or missing counterpart (TV/Admin not present)
- Non-fatal asset missing (optional image not found)
- External dependency slow/retry (serial device not found yet)

### Examples that should be ERROR
- Embedded DB fails to start
- SQL bootstrap fails
- Corrupt asset bundle (cannot read required MP3)
- Unhandled exception in controller/service path

## What to log (project-specific)

### Game lifecycle
Log the “milestones” that explain a full run:

- roomCode
- stage transitions
- selected album/category id
- scheduleId/songId transitions
- reveal events
- scoring changes (at least summary)

### Reconnect / state reconstruction
When reconstructing UI state after reconnect, log enough to diagnose “why did the UI look like that?” 

Include (at DEBUG or INFO depending on noise):
- roomCode
- stage
- scheduleId/songId
- whether revealed
- computed seek/remaining (if applicable)
- active interrupt type (team/system) and interruptId

### WebSockets
WebSockets can be high-frequency. Guidelines:
- **INFO** only for connect/disconnect and major broadcasts (“welcome snapshot sent”).
- **DEBUG** for message handling if needed, but keep it short.
- Avoid logging every “tick” or repeated playback status updates.



## Logging etiquette (keep logs readable)

- Use **structured, stable wording**: logs should be greppable.
- Put variable data into parameters, not string concat:
  - ✅ `log.info("Stage changed: room={} from={} to={}", roomCode, oldStage, newStage)`
  - ❌ `log.info("Stage changed: " + roomCode + " ...")`
- Prefer one log per event; don’t log the same failure in multiple layers unless needed.
- Avoid huge payload dumps (especially full JSON snapshots). If necessary:
  - log identifiers + key flags
  - log full payload only at TRACE with truncation



## Correlation and context (MDC)

For troubleshooting multi-request flows, logs should carry correlation fields using **MDC** where possible:

Recommended keys:
- `roomCode`
- `requestId` (or traceId if available)
- `client` (ADMIN/TV) when relevant

**Rule:** set MDC in entry points (controllers / WS connect handlers) and clear it afterward.

This makes it much easier to follow a single room/session in log files.


## Security & privacy policy

Never log:
- passwords, tokens, secrets, DB credentials
- private user data (if introduced later)
- raw file contents

Song answers:
- Avoid logging answers/titles in contexts where it might leak gameplay information.
- If needed for debugging, restrict to DEBUG and ensure production config defaults to INFO.



## Exception logging policy

- Controllers should rely on the global exception handler (single place to format errors).
- Services should log **context** (roomCode, ids) and rethrow, instead of logging stack traces repeatedly.
- For known domain exceptions (e.g., DerivedException family):
  - log at WARN for user-caused issues
  - log at ERROR for internal failures (unexpected states)

**Rule of thumb:** one stack trace per incident.



## Configuration guidance

Logging configuration should be centralized and predictable:

- package-specific levels in `application*.yml`
- Logback rolling policy in `logback-spring.xml` (or equivalent)
- production profile should set:
  - stable file path
  - INFO by default
  - DEBUG only temporarily during investigations



## Operational troubleshooting checklist

When someone reports “it didn’t work”:
1. Find the log folder for their OS build.
2. Search for:
   - roomCode
   - “Stage changed”
   - “interrupt”
   - “asset”
   - “startup” / “embedded postgres”
3. Confirm:
   - app started successfully
   - DB started (if embeddb)
   - assets base dir resolved
   - WS connect/disconnect sequence


