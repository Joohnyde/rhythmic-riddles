# Security model

## Phase 1 (current)
The current implementation is designed for a **trusted local network** (single laptop / venue).

- WebSockets are accepted for a valid room code and socket position (0/1).
- Most REST endpoints require `ROOM_CODE` header.
- There is **no password/token enforcement** in the backend yet.

**Important implication:** anyone on the same network who knows the room code could call endpoints.
For public venues, treat the laptop as the trusted boundary.

## Non-leak principle (critical)
The TV must never receive answers.

Enforce this in:
- backend: do not include answer fields in TV-targeted messages
- frontend: TV UI must not request answer endpoints
- logs: avoid logging answers in contexts that could be shown publicly

## Planned hardening (recommended)
1. Add “join password” at game creation (Admin sets it).
2. Replace password-in-storage with short-lived session token:
   - Admin token
   - TV token (restricted claims)
3. Rate limit critical endpoints (buzz/answer)
4. Add audit logs:
   - admin actions
   - stage transitions
   - scoring changes

## Secrets handling
- never commit real credentials in `application.yml`
- use `.env.example` templates
- store CI secrets in GitHub Actions secrets

## SaaS future notes
If hosted:
- terminate TLS at ingress (HTTPS/WSS)
- use per-tenant isolation (db schema or separate db)
- object storage for assets (S3/MinIO)
- least-privilege service accounts

