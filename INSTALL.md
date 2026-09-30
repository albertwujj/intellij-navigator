# Installation Guide

## Requirements

- Tested with PyCharm.
- Both plugins declare an IDE build range of `241`–`251.*` (2024.1–2025.1). See [JetBrains' build number reference](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html) for the version mapping.
- The backend requires the Python module (`com.intellij.modules.python`) and Git plugin (`Git4Idea`). Other IntelliJ-based IDEs must provide these dependencies too.

## Installation

Download both plugin ZIPs from the same [IntelliJ Navigator release](https://github.com/albertwujj/intellij-navigator/releases):

- `intellij-navigator-<version>.zip` — backend plugin
- `intellij-navigator-frontend-<version>.zip` — frontend plugin

Where to install them:

- **Local IDE**: install both plugins in the same IDE
- **Remote Development / WSL**: install the backend plugin on the **Host** IDE and the frontend plugin on the **Client** IDE

Install each zip through **Settings** → **Plugins** → **⚙️** → **Install Plugin from Disk...**, then restart the IDE.

AgentTerm connects to the plugins directly over TCP. Netcat is not required.

## Verify Installation

1. Open the same project in your IDE and AgentTerm.
2. Click a code file-and-line reference in the agent's output. The IDE should open that location.
3. Try quoting the IDE's current file and line into the agent prompt, as described in [AgentTerm's IDE guide](https://github.com/albertwujj/agent-term/blob/main/docs/ide.md).

<a name="platform-setup"></a>
<a name="macos"></a>
<a name="linux"></a>
<a name="wsl-windows-subsystem-for-linux"></a>
<a name="windows-native"></a>
<a name="connection-refused"></a>
<a name="port-already-in-use"></a>
<a name="plugin-not-loading"></a>

## Troubleshooting

See [optional diagnostics](DIAGNOSTICS.md) for manual socket checks, Windows and WSL details, port conflicts, and IDE logs.

## Building from Source

See [CLAUDE.md](CLAUDE.md) for build instructions.
