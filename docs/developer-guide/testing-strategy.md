# Testing strategy

Testing goals:
- prevent regressions in state machine and recovery logic
- catch API contract breaks between backend and the two UIs
- enforce quality gates in CI (no green build → no merge)

## Backend (Spring Boot)
### Unit tests
Use JUnit 5 and test:
- service methods (IgraService, OdgovorService, KategorijaService)
- seek computation logic
- invariants around interrupts

### Slice tests
- repository queries (KategorijaRepository, RedoslijedRepository, etc.)
- controller advice / exception mapping

### Integration tests (high value)
Run against a real Postgres instance (Testcontainers recommended):
- end-to-end stage transitions
- reconnect behavior (simulate disconnect by writing technical interrupt)

## Frontend (Angular)
Recommended split:
- unit tests: components + services
- integration tests: state machine flows (fake websocket + http)
- end-to-end tests: Playwright or Cypress

High-value E2E scenarios:
- lobby → albums → songs → winner
- buzz + correct answer
- buzz + wrong answer + resume playback
- disconnect TV during song → pause → reconnect → continue
- disconnect Admin during song → pause → reconnect → continue

## CI gates (what we want)
On PR:
- backend: build + tests
- frontend: lint + tests + build
- formatting checks
- dependency scanning (Dependabot / CodeQL recommended)

Current GitHub Actions workflows include placeholders; wire them in as the next engineering task.

## Your Serbian test list (converted to backlog)
Your original notes are an excellent source for integration tests. Track them as issues:
- socket handshake sanity (room exists, pos 0/1, etc.)
- create game params validation
- team creation and kick constraints
- illegal stage transitions
- interrupt rules (team interrupt only in stage 2, only while song playing, etc.)
- all context-switch/reconnect combinations

When adding a feature:
1. write tests first (or alongside)
2. ensure it passes in CI
3. document the new behavior in `state-machine-and-recovery.md` and API doc

