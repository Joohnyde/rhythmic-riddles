# Logging

## Current state (Phase 1)
Backend currently uses `java.util.logging.Logger` directly in controllers and services.

Pros:
- zero dependencies
- works out of the box

Cons:
- inconsistent formatting
- hard to configure levels/appenders/rolling
- not ideal for production

## Recommended next step
Move to a standard Spring Boot logging stack:
- SLF4J API
- Logback (default in Spring Boot) or Log4j2 (if you prefer)
- rolling file appender (daily + size limits)
- structured JSON logs (optional) for SaaS deployments

### Minimal migration plan
1. Replace `java.util.logging.Logger` usage with `org.slf4j.Logger` + `LoggerFactory`
2. Add per-package log levels in `application.yml`
3. Add rolling file configuration

## Logging policy
- Do not log secrets
- Do not log answers (song title/artist) in TV-visible contexts
- Log game transitions and recovery decisions (stage changes, interrupts, continues)
- Use levels consistently:
  - INFO: expected lifecycle events
  - WARN: recoverable issues (missing socket, retry)
  - ERROR: failed operations that require developer attention

## What to log for recovery
When deriving UI state after a reconnect, log:
- last schedule/track id
- computed seek
- whether a technical pause is active
- whether a team is currently answering

This makes post-mortems possible without replaying the whole DB.

