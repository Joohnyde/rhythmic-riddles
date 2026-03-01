
# Assets Architecture (Audio & Images)

This document defines how RhythmicRiddles manages audio snippets, answer tracks, and image assets.

It replaces previous fragmented documentation and aligns strictly with the current implementation:

- `AssetProperties`
- `LocalAssetGateway`
- `SongServiceImpl`

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

- Business logic (SongService, controllers)
- Physical storage (filesystem)

Current implementation:

```
SongServiceImpl
    ↓
AssetGateway (interface)
    ↓
LocalAssetGateway (filesystem implementation)
```

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

Images are resolved dynamically by extension.

Supported extensions:

- png
- jpg
- jpeg
- webp

Resolution logic:

The gateway checks each extension in order and returns the first existing match.

This allows:
- flexible image formats
- no hardcoded extension assumptions

If no image is found → `Optional.empty()` is returned.


## Error Handling Strategy

Audio methods throw `DerivedException` subclasses:

- `AssetAccessException.Reason.NOT_FOUND`
- `AssetAccessException.Reason.UNREADABLE`

This integrates cleanly with the global exception mapping.

Images return Optional:
- absence is not necessarily an error
- teams/albums may legitimately have no image


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
- Never commit images
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

