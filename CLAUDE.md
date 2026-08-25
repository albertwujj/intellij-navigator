# Build

Requires JDK 17+. Use PyCharm's bundled JDK:

```bash
export JAVA_HOME="/Applications/PyCharm CE.app/Contents/jbr/Contents/Home"
```

## Commands

- Build backend plugin: `./gradlew buildPlugin`
- Build frontend plugin: `cd frontend-plugin && ./gradlew buildPlugin`
- Run backend tests: `./gradlew test`
- Run frontend tests: `cd frontend-plugin && ./gradlew test`
- Backend output: `build/distributions/intellij-navigator-*.zip`
- Frontend output: `frontend-plugin/build/distributions/intellij-navigator-frontend-*.zip`

## Releases

This repository owns both plugin versions and their downloadable artifacts. Keep `pluginVersion` in `gradle.properties` and `frontend-plugin/gradle.properties` synchronized.

- `./scripts/release.sh --check` — test and build both plugins, then validate the ZIPs without publishing
- `./scripts/release.sh` — repeat those checks, tag and push the synchronized plugin version, and publish both ZIPs to `albertwujj/intellij-navigator`

AgentTerm does not host plugin assets and has no application release workflow.

## Architecture

Two separate plugins that work together:

- **Backend plugin** (port 8765): Resolves files/symbols, opens or activates files, moves caret when requested.
  Runs on the IDE that has access to the project (local PyCharm or WSL backend).
- **Frontend plugin** (port 8766): Scrolls the editor to the current caret position.
  Runs on the IDE that has a real display (local PyCharm or thin client on Windows).

On local dev, both plugins run in the same PyCharm instance. On WSL remote dev,
the backend runs on WSL and the frontend runs on the JetBrains thin client.

Navigation flow (agent-term):
1. Send request to port 8765
2. If the backend response includes `line`, send scroll request to port 8766 → frontend calls `scrollToCaret(CENTER)`

## E2E Testing

Use the sandbox IDE — it loads both plugins automatically, no manual install needed.

### Build and launch sandbox

```bash
# Build both plugins and launch sandbox (one-liner)
cd frontend-plugin && ./gradlew buildPlugin && cd .. && ./gradlew runIde
```

Wait for the IDE to open and a project to load (~30s), then verify both ports:

```bash
lsof -i :8765 | grep LISTEN && lsof -i :8766 | grep LISTEN
```

**Note:** The sandbox must be launched from the root project (`./gradlew runIde`),
not from `frontend-plugin/`. The root `runIde` copies the frontend plugin into the
sandbox and disables sandbox mode so both plugins load.

### Sending requests

Use `printf` + `nc` (not `echo`, to avoid shell quoting issues with JSON):

```bash
# Step 1: backend opens file and moves caret
printf '{"type":"file","path":"some_file.py","line":50}\n' | nc -w 3 localhost 8765

# Step 2: frontend scrolls to caret (only needed when step 1 returns status "ok")
printf '{"action":"scroll","file":"some_file.py","line":50,"column":0}\n' | nc -w 3 localhost 8766

# Backend-only file activation (preserves remembered editor state)
printf '{"type":"file","path":"some_file.py"}\n' | nc -w 3 localhost 8765

# Get current caret position (works in regular editors and unified diff views)
printf '{"type":"caret"}\n' | nc -w 3 localhost 8765
```

See API.md for response format and status codes.

### Sanity check categories

- **Exact lookup**: class, function by name
- **Constants/variables**: module-level assignments, namedtuples
- **Partial matching**: prefix (`Warm` → `Warmup`), case-insensitive (`warmup` → `Warmup`), camelCase (`UV` → `URLValue`)
- **Qualified**: `Class.method`, `module.Class`
- **self/cls stripping**: `self.method` → `method`
- **Soft qualifier fallback**: `WrongClass.method` still finds `method`
- **Negative**: nonexistent symbols return `{"status":"error"}`
