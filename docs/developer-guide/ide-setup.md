# IDE setup & formatting rules

Goal: **no formatting wars** and minimal diff noise.

## One source of truth: `.editorconfig`
This repo includes a root `.editorconfig`. It defines:
- UTF-8
- LF line endings
- final newline required
- trim trailing whitespace (Markdown exempt)
- indentation:
  - Java: 4 spaces
  - TS/HTML/CSS/JSON/YAML: 2 spaces

### Recommended IDEs
- Backend: Apache NetBeans / IntelliJ / VS Code
- Frontend: VS Code (recommended), WebStorm

### Make EditorConfig active
- VS Code:
    - Install EditorConfig extension (editorconfig.editorconfig)
    - Set as default formatter ( e.g. by setting ``` "editor.defaultFormatter": "EditorConfig.EditorConfig" ``` in settings.json)
- IntelliJ:
    - EditorConfig extension should come bundled, install if not
    - Make sure Settings -> Editor -> Code Style -> Enable EditorConfig support is checked
    - **Note:** comment language recognition from .editorconfig is not supported. To ensure consistency, make sure Settings -> Editor -> Natural Languages is set to English (US)
- NetBeans: ensure EditorConfig support is enabled (plugin may be required depending on version)

## Java formatting
Currently, formatting is enforced mainly by:
- `.editorconfig` basics
- team conventions

Planned (recommended) next step:
- add Spotless (Maven) with google-java-format or Eclipse formatter
- enforce `mvn spotless:check` in CI

Until then:
- avoid “format on save” unless consistent across the team
- keep commits focused (no reformat-only commits mixed with logic)

## Angular formatting
Current frontend uses:
- ESLint + Prettier conventions (wire into CI next)

Recommended:
- `npm run lint`
- `npm run format` (once added)

## Scripts
- `./scripts/dev/format-all.sh` formats everything (when configured)
- `./scripts/dev/test-all.sh` runs backend + frontend tests

> If a script references TODO commands, it means the pipeline is still being wired in.

