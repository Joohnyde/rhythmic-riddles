# Exceptions and Error Codes

**Document:** `exceptions.md`

This file defines the **canonical error contract** for REST endpoints.

## Error philosophy

The backend uses **domain exceptions** to return predictable, frontend-friendly errors.

All domain exceptions extend:

- `DerivedException` (`com.cevapinxile.cestereg.common.exception.DerivedException`)

These exceptions are intended to be propagated to clients (as opposed to programmer errors / 500s).

## How error responses are produced

### DerivedException fields

`DerivedException` carries:

- `HTTP_CODE` — the HTTP status to return
- `ERROR_CODE` — a stable short code (project uses numeric strings like `"004"`)
- `TITLE` — short category label
- `message` — detailed message string

### Response body format (important)

`DerivedException.toString()` returns a **JSON string** which is used as the response body:

```json
{ "error": "E004 - App not reachable", "message": "TV app has to be connected to proceed" }
```

Notes:

- The response is **stringified JSON** (not a typed DTO).
- The `error` field is built as `"E" + ERROR_CODE + " - " + TITLE"`.
- In code, `ERROR_CODE` is typically `"004"`, `"007"`, etc. (without the leading `E`).

## Error code catalog (canonical)

| Code | HTTP | Title                     | Exception class                           | Typical meaning                                                                     |
| ---- | ---: | ------------------------- | ----------------------------------------- | ----------------------------------------------------------------------------------- |
| E000 |  400 | An argument is missing    | `MissingArgumentException`                | Missing required request body/argument                                              |
| E001 |  404 | Invalid referenced object | `InvalidReferencedObjectException`        | Referenced entity not found                                                         |
| E002 |  422 | Malformed argument        | `InvalidArgumentException`                | Invalid value, mismatch, illegal combination                                        |
| E003 |  409 | Wrong game-state          | `WrongGameStateException`                 | Wrong stage / illegal state                                                         |
| E004 |  503 | App not reachable         | `AppNotRegisteredException`               | Required client not connected (Admin/TV presence gate)                              |
| E005 |  401 | Unauthorized request      | `UnauthorizedException`                   | Team tries to act outside its game                                                  |
| E006 |  409 | Guess wasn't allowed      | `GuessNotAllowedException`                | Guess not allowed (paused / already guessed / song ended / etc.)                    |
| E007 |  404 | Asset Not Found           | `AssetAccessException(Reason.NOT_FOUND)`  | Requested audio/image asset is missing                                              |
| E008 |  503 | Asset Unavailable         | `AssetAccessException(Reason.UNREADABLE)` | Resolved audio/image asset cannot be read / storage issue                           |
| E009 |  400 | Invalid e2e game fixture  | `E2eGameFixtureValidationException`       | E2E seed fixture is syntactically valid but semantically impossible                 |
| E010 |  423 | Room busy                 | `RoomBusyException`                       | Another request already owns the room mutation lock; retry against the latest state |
| E999 |  500 | Internal Server Error     | `InternalServerErrorException`            | Unexpected internal error occured                                                   |

## Where you’ll see each error (practical map)

### E000 — Missing argument (400)

You’ll see this when a controller expects a request body/param and it’s absent.

### E001 — Invalid referenced object (404)

Game/team/category/schedule/interrupt does not exist or cannot be resolved.

### E002 — Malformed argument (422)

Input exists but is invalid:

- invalid stageId/scenario
- category/team doesn’t belong to the given room
- inconsistent references

### E003 — Wrong game-state (409)

Operation is called in the wrong stage or illegal progression.

### E004 — App not reachable (503)

A presence gate is enforced (typically **Admin + TV must both be connected**).

### E005 — Unauthorized request (401)

A team tries to buzz/act in a game it does not belong to.

### E006 — Guess wasn’t allowed (409)

Buzz/answer rules reject the action:

- system pause active
- team already guessed
- another team currently answering
- snippet already ended or revealed

### E007 / E008 — Asset errors (404 / 503)

Thrown by `AssetAccessException` for both audio and image assets:

- `NOT_FOUND` → E007 / 404 when the requested asset file is absent
- `UNREADABLE` → E008 / 503 when the resolved asset cannot be read

Album image requests surface the same contract through `GET /assets/v1/image/albums/{albumId}`.

### E009 — Invalid E2E game fixture (400)

The E2E seed payload is syntactically valid but represents an invalid or inconsistent game state.

This is used only by the E2E seed endpoint to reject bad test setup before persistence.

### E010 — Room busy (423)

A fail-fast room mutation could not acquire the PostgreSQL `FOR UPDATE NOWAIT` lock because another request is already changing the same game. The client should retry against the latest state.

### E999 — Internal Server Error (500)

Error that endpoints return when an unexpected Exception is caught.

## How to add a new error

1. Create a new subclass of `DerivedException`
2. Pick the next numeric `ERROR_CODE`
3. Use:

- correct HTTP status
- short, stable title
- safe message (no secrets, no answers)

4. Add an entry to this table
5. Add tests that assert:

- HTTP status
- error code
- message behavior
