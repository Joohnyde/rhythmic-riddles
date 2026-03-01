# Exceptions and Error Codes
**Document:** `exceptions.md`

This file defines the **canonical error contract** for REST endpoints.

---

## 1) Error philosophy

The backend uses **domain exceptions** to return predictable, frontend-friendly errors.

All domain exceptions extend:

- `DerivedException` (`com.cevapinxile.cestereg.common.exception.DerivedException`)

These exceptions are intended to be propagated to clients (as opposed to programmer errors / 500s).

---

## 2) How error responses are produced

### 2.1 DerivedException fields
`DerivedException` carries:

- `HTTP_CODE` — the HTTP status to return
- `ERROR_CODE` — a stable short code (project uses numeric strings like `"004"`)
- `TITLE` — short category label
- `message` — detailed message string

### 2.2 Response body format (important)
`DerivedException.toString()` returns a **JSON string** which is used as the response body:

```json
{"error":"E004 - App not reachable" ,"message":"TV app has to be connected to proceed"}
```

Notes:
- The response is **stringified JSON** (not a typed DTO).
- The `error` field is built as `"E" + ERROR_CODE + " - " + TITLE"`.
  - In code, `ERROR_CODE` is typically `"004"`, `"007"`, etc. (without the leading `E`).

---

## 3) Error code catalog (canonical)

| Code | HTTP | Title | Exception class | Typical meaning |
|---|---:|---|---|---|
| E000 | 400 | An argument is missing | `MissingArgumentException` | Missing required request body/argument |
| E001 | 404 | Invalid referenced object | `InvalidReferencedObjectException` | Referenced entity not found |
| E002 | 422 | Malformed argument | `InvalidArgumentException` | Invalid value, mismatch, illegal combination |
| E003 | 409 | Wrong game-state | `WrongGameStateException` | Wrong stage / illegal state for operation |
| E004 | 503 | App not reachable | `AppNotRegisteredException` | Required client not connected (Admin/TV presence gate) |
| E005 | 401 | Unauthorized request | `UnauthorizedException` | Team tries to act outside its game |
| E006 | 409 | Guess wasn't allowed | `GuessNotAllowedException` | Guess not allowed (paused / already guessed / song ended / etc.) |
| E007 | 404 | Asset Not Found | `AssetAccessException(Reason.NOT_FOUND)` | MP3 missing |
| E008 | 503 | Asset Unavailable | `AssetAccessException(Reason.UNREADABLE)` | MP3 exists but cannot be read / storage issue |

---

## 4) Where you’ll see each error (practical map)

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
Thrown by `AssetAccessException`:
- `NOT_FOUND` → E007 / 404
- `UNREADABLE` → E008 / 503

---


## 5) How to add a new error
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


