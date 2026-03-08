

# EditorConfig

This project uses **EditorConfig** as a lightweight, cross-editor “baseline formatter” to prevent noisy diffs and keep the codebase consistent across operating systems and IDEs.

EditorConfig does **not** replace language formatters (Prettier, google-java-format, etc.). It defines the **minimum** rules that every editor should follow: encoding, line endings, indentation, and whitespace handling.



## What EditorConfig does here

The repo includes a root `.editorconfig` that defines:

- `charset = utf-8`
- `end_of_line = lf` (prevents Windows CRLF churn)
- `insert_final_newline = true`
- `trim_trailing_whitespace = true` (**except** Markdown)
- indentation defaults:
  - **4 spaces** for most files (including Java)
  - **2 spaces** for frontend + config formats

Key file groups:

- Markdown (`*.md`)
  - trailing whitespace is **not trimmed** (common for Markdown formatting)
  - max line length guidance (`120`)

- Frontend + configs (`*.{ts,tsx,js,jsx,html,css,scss,yml,yaml,json,sh,ino}`)
  - indentation is **2 spaces**

- Java (`*.java`)
  - max line length guidance (`120`)



## Integrating EditorConfig into IDEs

### VS Code
1) Install the extension: **EditorConfig for VS Code** (`editorconfig.editorconfig`)
2) Ensure it’s enabled (it normally is immediately)
3) Optional: keep your “format on save” formatter (Prettier) — EditorConfig will still enforce EOL/indent basics

Recommended VS Code settings snippet:
```json
{
  "files.eol": "\n",
  "editor.formatOnSave": true
}
```

### IntelliJ IDEA / WebStorm
EditorConfig support is built-in (usually enabled by default).

Verify:
1) `Settings` → `Editor` → `Code Style`
2) Enable: **EditorConfig support**

Notes:
- IntelliJ uses EditorConfig for indentation and whitespace rules.
- For Java formatting style (braces/wrapping), use IntelliJ code style or a formatter like Spotless (see Future Improvements).


### NetBeans
Reference: https://github.com/welovecoding/editorconfig-netbeans



#### What you get

Once installed, NetBeans will read the repo’s `.editorconfig` and apply baseline rules such as:

- line endings (LF vs CRLF)
- charset (UTF-8)
- indentation size/style
- trimming trailing whitespace (where configured)
- final newline insertion

#### Installation (recommended: pre-built release)

The plugin is distributed as a NetBeans module (`.nbm`) via GitHub Releases

#### Step-by-step

1) Download the latest release [`.nbm` file](https://github.com/welovecoding/editorconfig-netbeans/releases/download/v0.10.4/editorconfig-0.10.4-SNAPSHOT.nbm) from GitHub Releases. 
2) Open NetBeans.
3) Go to:
   - **Tools → Plugins**
4) Open the **Downloaded** tab.
5) Click **Add Plugins…**
6) Select the downloaded `.nbm` file.
7) Click **Install** and follow the wizard.
8) Restart NetBeans when prompted.

This installation flow (Tools → Plugins → Downloaded) is the standard way to install a downloaded NetBeans module. 


## Verify it works

1) Open a file that is covered by `.editorconfig` (e.g., `.java`, `.ts`, `.yml`).
2) Make an indentation change (Tab / auto-indent) and ensure it matches the project rules.
3) On save, check:
   - the file keeps LF line endings
   - trailing whitespace behavior matches the rules
   - the file has a final newline

Tip: If your IDE shows mixed line endings, look for an indicator in the status bar and convert to LF, then save again.

### Notes / compatibility

- The plugin repo is archived and may not support every modern Apache NetBeans version perfectly. 
- If you hit issues:
  - confirm EditorConfig support is enabled (plugin is installed and active)
  - restart NetBeans after installation
  - try the newest available `.nbm` from Releases




## How EditorConfig interacts with language formatters

EditorConfig should be treated as a **baseline**, while language-specific tooling handles style:

- **Frontend**: Prettier + ESLint (recommended in CI)
- **Backend**: Spotless + google-java-format (recommended in CI)

EditorConfig prevents the “death by a thousand cuts” issues:
- mixed indentation
- trailing whitespace churn
- CRLF vs LF changes
- missing final newline diffs


## Future improvements (recommended)

### 1) Enforce formatting in CI
Add CI checks so formatting issues are caught automatically:
- Frontend: `npm run lint` + `npm run format:check`
- Backend: `mvn spotless:check`

### 2) Add Spotless for Java (Maven)
Use Spotless to standardize formatting across IDEs:
- google-java-format (simple, consistent)
- or Eclipse formatter config (more customizable)

### 3) Add a “format-all” developer script
Standardize one command for formatting the whole repo (backend + frontend), so devs don’t need to guess.
