# IntelliJ Navigator Plugin

IDE navigation plugins for [AgentTerm](https://github.com/albertwujj/agent-term). Enables AgentTerm (or any TCP client) to navigate IntelliJ-based IDEs to specific files, lines, and symbols.

Tested with PyCharm, compatible with all IntelliJ-based IDEs (WebStorm, GoLand, IntelliJ IDEA, etc.).

## Plugin Roles

Two plugins work together to handle navigation in JetBrains Remote Development:

| Plugin | Port | Install on | Role |
|--------|------|------------|------|
| **intellij-navigator** (backend) | 8765 | **Host** IDE | Resolves file paths and symbols, opens or activates files, moves the caret when requested |
| **intellij-navigator-frontend** | 8766 | **Client** IDE | Scrolls the editor viewport, reports the visible caret, and prevents accidental direct edits with a toggleable read-only editor guard |

In a local (non-remote) setup, install both plugins in the same IDE.

## Features

- Navigate to files by path (partial matching supported)
- Activate a file while preserving IntelliJ's remembered caret and scroll state
- Navigate to symbols (classes, functions, methods, variables, constants)
- Partial, case-insensitive, and camelCase symbol matching
- Search by code text when line numbers aren't available
- Report the visible file and caret position from the client IDE
- Prevent accidental direct edits in frontend project editors while keeping external AgentTerm edits working
- Selector popup for multiple matches

## Download

Pre-built backend and frontend plugin ZIPs are published together on this repository's [Releases page](https://github.com/albertwujj/intellij-navigator/releases).

AgentTerm itself runs directly from its `main` branch and does not publish application releases. Plugin versions and downloadable artifacts are owned by this repository.

## Documentation

- **[Installation Guide](INSTALL.md)** - Setup and configuration for users
- **[API Reference](API.md)** - Protocol specification for developers
- **[Local Split Mode](LOCAL_SPLIT_MODE.md)** - One-time bootstrap vs repeatable local Remote Dev workflow

## Quick Start

1. Build the plugin: `./gradlew buildPlugin` (output in `build/distributions/`)
2. Open a project in your IDE
3. Test:
   ```bash
   printf '{"type":"file","path":"your_file.py","line":1}\n' | nc localhost 8765
   ```

To switch to a file without forcing a new location, use:

```bash
printf '{"type":"file","path":"your_file.py"}\n' | nc localhost 8765
```

To read the visible caret from the client IDE, use:

```bash
printf '{"action":"caret"}\n' | nc localhost 8766
```

## License

MIT
