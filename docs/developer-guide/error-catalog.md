# Error catalog (backend)

The backend uses a custom `DerivedException` base class.

## Current wire format
Controllers typically return:

```json
{"error":"E003 - Wrong game-state","message":"..."}
```

Notes:
- `error` contains the error code prefixed with `E` and the title
- `message` is a human-readable string suitable for UI (avoid leaking answers)

## Catalog

| Code | HTTP | Title | Exception class | Typical cause |
|---|---:|---|---|---|
| `E000` | 400 | An argument is missing | `MissingArgumentException` | TODO: add concrete example from code paths |
| `E001` | 404 | Invalid referenced object | `InvalidReferencedObjectException` | TODO: add concrete example from code paths |
| `E002` | 422 | Malformed argument | `InvalidArgumentException` | TODO: add concrete example from code paths |
| `E003` | 409 | Wrong game-state | `WrongGameStateException` | TODO: add concrete example from code paths |
| `E004` | 503 | App not reachable | `AppNotRegisteredException` | TODO: add concrete example from code paths |
| `E005` | 401 | Unauthorized request | `UnauthorizedException` | TODO: add concrete example from code paths |
| `E006` | 409 | Guess wasn't allowed | `GuessNotAllowedException` | TODO: add concrete example from code paths |

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

> Planned improvement: return a structured error response object instead of JSON strings.

