# Using asset paths in code

## Rule: never hardcode `./pjesme/...`
All asset path logic must go through a single “asset path builder” (service/config).

This prevents:
- relative-path bugs (different working dirs)
- inconsistent naming (`_p` vs `_o`)
- scattered string concatenation

## Config property
Backend uses:

- `app.assets.base-dir`

Repo layout default (from `apps/quiz-game/backend`):
- `../../../data`

## Recommended implementation pattern

### 1) Bind properties
```java
@Component
@ConfigurationProperties(prefix = "app.assets")
public class AssetProperties {
  private String baseDir;
  // getters/setters
}
```

### 2) Centralize path building
```java
@Service
public class AssetPathService {
  private final Path base;

  public AssetPathService(AssetProperties props) {
    if (props.getBaseDir() == null || props.getBaseDir().isBlank()) {
      throw new IllegalStateException("Missing app.assets.base-dir");
    }
    this.base = Path.of(props.getBaseDir()).toAbsolutePath().normalize();
  }

  public Path questionSnippet(UUID songId) {
    return base.resolve("audio/snippets").resolve(songId + "_p.mp3");
  }

  public Path answerSnippet(UUID songId) {
    return base.resolve("audio/answers").resolve(songId + "_o.mp3");
  }

  public Path teamIcon(UUID teamId, String ext) {
    return base.resolve("images/teams").resolve(teamId + "." + ext);
  }

  public Path albumCover(UUID albumId, String ext) {
    return base.resolve("images/albums").resolve(albumId + "." + ext);
  }
}
```

## Common pitfalls (and how we avoid them)
- **Working directory differs** between IDE, CLI, Docker → resolve to absolute in constructor and log it
- **Wrong folder names** (`answer` vs `answers`) → centralized service prevents drift
- **Wrong filename suffix** (`<uuid>.mp3` vs `<uuid>_p.mp3`) → centralized service enforces convention

