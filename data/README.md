# data/ (runtime assets)

This folder represents the **runtime asset store** for RhythmicRiddles.

It is expected to contain:
- audio snippets for questions
- audio snippets for answers
- images for teams and albums

In most setups, the real `data/` directory is **not committed to Git** and is provided externally (or packaged into desktop releases).

## Expected structure
- `audio/snippets/<songId>.mp3`
- `audio/answers/<songId>.mp3`
- `images/teams/<teamId>.<ext>`
- `images/albums/<albumId>.<ext>`

The backend resolves these paths using the configured `app.assets.base-dir` and the AssetGateway abstraction.

## Documentation
See `docs/developer-guide/assets.md`.
