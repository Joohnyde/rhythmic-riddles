# Asset storage (audio + images)

This repo stores large binary assets (MP3 snippets and images) **outside git**.

## Folder layout (Phase 1)

```
data/
  audio/
    snippets/
      <songUuid>_p.mp3        # question snippet ("pitanje", ~10s)
    answers/
      <songUuid>_o.mp3        # answer snippet ("odgovor", ~6-7s)
    songs/
      <songUuid>.mp3          # optional: full-length audio (not required for quiz-game)
  images/
    teams/
      <teamUuid>.<ext>
    albums/
      <albumUuid>.<ext>
```

### Naming rules
- `*_p.mp3` = question snippet
- `*_o.mp3` = answer snippet

## Why local filesystem (for now)
For the current “single laptop at venue” model:
- filesystem is fastest and simplest
- no extra infra
- easy backup (`tar -czf data-backup.tgz data/`)

We keep the code ready for future object storage (S3/MinIO) by centralizing path logic.

## Git rules
- `data/` is gitignored
- never commit MP3 or image binaries
- use scripts to seed dev data instead of committing it

## Migration from legacy `pjesme/`
If you had:
- `pjesme/<uuid>_p.mp3`
- `pjesme/<uuid>_o.mp3`

Move them into the new structure:

```bash
mkdir -p data/audio/snippets data/audio/answers
mv pjesme/*_p.mp3 data/audio/snippets/ 2>/dev/null || true
mv pjesme/*_o.mp3 data/audio/answers/ 2>/dev/null || true
```

## Configuration
Backend reads assets from:

- `app.assets.base-dir`

In this repo layout (backend under `apps/quiz-game/backend`), the default is:

```yaml
app:
  assets:
    base-dir: ../../../data
```

That resolves to the repo root `data/` folder when backend is started from its project directory.

See `docs/developer-guide/assets-paths-in-code.md`.

