
# Assets Architecture (Audio & Images)

This document defines how RhythmicRiddles manages audio snippets, answer tracks, and image assets.

It replaces previous fragmented documentation and aligns strictly with the current implementation:

- `AssetProperties`
- `AssetGateway` / `LocalAssetGateway`
- `SongServiceImpl`
- `ImageServiceImpl`
- `ImageAssetController`

This is the **single source of truth** for how assets are stored, resolved, accessed, and abstracted.


## Design Goals

The asset subsystem was designed with the following goals:

- No hardcoded paths
- Environment-independent resolution (IDE, CLI, Docker, production package)
- Git-safe (large binaries not committed)
- Replaceable storage backend (filesystem now, object storage later)
- Clear separation of concerns (via Gateway pattern)


## Storage Strategy (Current Implementation)

Assets are stored on the **local filesystem**, outside Git.

Configured via:

```
app.assets.base-dir
```

Bound using:

```java
@ConfigurationProperties(prefix = "app.assets")
public class AssetProperties {
    private String baseDir;
}
```

The base directory must point to the `data/` folder.

Example (dev repo layout):

```yaml
app:
  assets:
    base-dir: ../../../data
```


## Folder Structure (Authoritative)

Under `base-dir` the expected structure is:

```
data/
  audio/
    snippets/
      <songId>.mp3
    answers/
      <songId>.mp3
  images/
    teams/
      <teamId>.(png|jpg|jpeg|webp)
    albums/
      <albumId>.(png|jpg|jpeg|webp)
```

This matches `LocalAssetGateway` exactly.

## AssetGateway Concept

### What is AssetGateway?

`AssetGateway` is an abstraction layer between:

- application services (`SongServiceImpl`, `ImageServiceImpl`)
- physical storage (the local filesystem today)

Current implementation:

```text
AudioAssetController -> SongServiceImpl  --+
                                          +-> AssetGateway -> LocalAssetGateway
ImageAssetController -> ImageServiceImpl --+
```

The gateway returns raw `byte[]` for MP3 assets and an `ImageAsset` record for images. `ImageAsset` carries both the exact bytes and the MIME type selected from the resolved extension, so HTTP code does not need to guess the image format.

Example:

```java
public byte[] playSnippet(UUID songId) {
    return assetGateway.readSnippetMp3(songId);
}
```

SongService does NOT know:
- where files are stored
- how paths are built
- what extensions are used
- whether storage is local, S3, MinIO, etc.

It only depends on the interface.


## Why Gateway Pattern Is Important

Benefits:

### 1) Replaceable storage backend
We can later introduce:

- `S3AssetGateway`
- `MinioAssetGateway`
- `DatabaseAssetGateway`

Without changing business logic.

### 2) Testability
We can mock `AssetGateway` in unit tests without touching the filesystem.

### 3) Centralized path logic
All resolution logic is inside one component:

```java
basePath
  .resolve("audio")
  .resolve(type.folder())
  .resolve(songId + ".mp3");
```

No scattered string concatenation.

### 4) Future-proofing
Today: single laptop model.
Tomorrow: SaaS with object storage.

Architecture already supports that transition.


## 6. Audio Handling

### Snippets

Resolved as:

```
audio/snippets/<songId>.mp3
```

Method:

```java
readSnippetMp3(UUID songId)
```

Throws `AssetAccessException` if:

- NOT_FOUND
- UNREADABLE

### Answers

Resolved as:

```
audio/answers/<songId>.mp3
```

Method:

```java
readAnswerMp3(UUID songId)
```

## Image Handling

Images are resolved dynamically by extension. `LocalAssetGateway` checks supported formats in this deterministic order:

| Extension | MIME type |
|---|---|
| `.png` | `image/png` |
| `.jpg` | `image/jpeg` |
| `.jpeg` | `image/jpeg` |
| `.webp` | `image/webp` |

The first regular file found is returned as `ImageAsset(byte[] bytes, String mimeType)`. If multiple files exist for the same UUID, the extension order above defines which one wins.

Album images are exposed through:

```text
GET /assets/v1/image/albums/{albumId}
```

The controller returns the exact stored bytes and uses the `ImageAsset.mimeType()` value as the HTTP `Content-Type`. Album image files under `images/albums` use the album UUID as their basename and may be stored as PNG, JPG/JPEG, or WebP. `CategorySimple.image` and `LastCategory.chosenCategoryPreview.image` carry that album UUID, which clients pass to the endpoint above; the backend resolves the matching file extension and MIME type. Team images use the same gateway resolution under `images/teams`, but no public team-image HTTP endpoint exists yet.

Missing images are not represented by `Optional.empty()`. A missing supported file is a public asset error (`E007 / 404`), while a file that resolves but cannot be read is `E008 / 503`.



## Generated Team Icon Catalog

Team icons used by the Stage 0 lobby live in `apps/frontend/public/team-icons/`. The frontend does not keep a second hand-maintained icon list. Instead, `scripts/generate-team-icons.ts` scans that directory and writes `src/app/domain/game/generated/team-icons.generated.ts`.

Icon filenames are part of the UI contract and must end in a six-digit hexadecimal color followed by `.png` or `.jpg`, for example:

```text
cat_A94DFB.png
record_FF6A5F.jpg
```

The suffix is used as the team text/accent color, keeping the visual treatment paired with the icon asset itself. Invalid image filenames make generation fail instead of silently producing an icon without a usable color.

Generation runs automatically before the normal npm entry points that need the catalog:

- `npm start`
- `npm run build`
- `npm run watch`
- `npm test`

The generated TypeScript file is intentionally gitignored. The source icon directory remains tracked so a fresh checkout has an authoritative place for icon assets. If an icon is added or renamed, rerun `npm run generate:team-icons` (or use one of the commands above). Direct Angular CLI commands such as `ng serve` bypass npm lifecycle hooks and therefore require generation to be run manually first. Adding icons while the application is already running is not supported; regenerate and rebuild/restart instead.

## Error Handling Strategy

Audio and image gateway methods use the same `AssetAccessException` contract:

- `AssetAccessException.Reason.NOT_FOUND` → `E007 - Asset Not Found` / HTTP `404`
- `AssetAccessException.Reason.UNREADABLE` → `E008 - Asset Unavailable` / HTTP `503`

The asset controllers pass these `DerivedException` responses through the standard API error handler. Unexpected controller failures use `E999 - Internal Server Error` / HTTP `500`.


## Absolute Path Normalization

In constructor:

```java
this.basePath = Path.of(props.getBaseDir())
    .toAbsolutePath()
    .normalize();
```

This avoids:

- relative path inconsistencies
- IDE vs CLI mismatches
- Docker working directory issues

The base path is logged at startup.


##  Git & Repository Policy

- `data/` is gitignored
- Never commit MP3 files
- Never commit runtime/user-provided images from `data/`
- Frontend-owned static assets (including `public/team-icons/`) are committed with the application
- Dev data must be seeded via scripts

This keeps repository clean and lightweight.

## Asset Extraction in Production

When running packaged desktop builds:

- Assets are extracted or copied into a persistent data directory.
- `app.assets.base-dir` is overridden at runtime.
- LocalAssetGateway operates identically regardless of environment.

This ensures:

- Desktop users do not need manual setup.
- Dev and production use the same gateway logic.

## Operational Notes

For venue deployments:

- Filesystem is fastest and simplest.
- Backup via:
  tar -czf data-backup.tgz data/

For SaaS future:

- Swap implementation.
- Keep interface stable.

## Summary

The asset system is:

- Simple (filesystem-based)
- Cleanly abstracted (Gateway pattern)
- Safe (no git bloat)
- Replaceable (future storage backends)
- Environment-agnostic (via configuration)

It balances pragmatism (3-dev team) with professional architectural discipline.

