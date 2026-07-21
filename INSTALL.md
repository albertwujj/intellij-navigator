# Installation Guide

## Requirements

- IntelliJ-based IDE (PyCharm, WebStorm, GoLand, IntelliJ IDEA, etc.)
- Version 2024.1 or later

## Installation

Install the plugin zips from the matching AgentTerm release:

- `intellij-navigator-<version>.zip` — backend plugin
- `intellij-navigator-frontend-<version>.zip` — frontend plugin

Where to install them:

- **Local IDE**: install both plugins in the same IDE
- **Remote Development / WSL**: install the backend plugin on the **Host** IDE and the frontend plugin on the **Client** IDE

Install each zip through **Settings** → **Plugins** → **⚙️** → **Install Plugin from Disk...**, then restart the IDE.

## Verify Installation

1. Open a project in your IDE
2. Run this command in your terminal:
   ```bash
   echo '{"type":"file","path":"any_file.py","line":1}' | nc localhost 8765
   ```
3. The IDE should jump to the file

## Platform Setup

### macOS

Works out of the box with `nc` (netcat).

### Linux

```bash
# Install netcat if needed
sudo apt-get install netcat-openbsd  # Debian/Ubuntu
sudo yum install nc                   # RHEL/CentOS
```

### WSL (Windows Subsystem for Linux)

When connecting from WSL to your IDE on Windows:

1. **Find Windows host IP:**
   ```bash
   cat /etc/resolv.conf | grep nameserver | awk '{print $2}'
   ```

2. **Connect using that IP:**
   ```bash
   WINDOWS_HOST=$(cat /etc/resolv.conf | grep nameserver | awk '{print $2}')
   echo '{"type":"file","path":"test.py","line":1}' | nc $WINDOWS_HOST 8765
   ```

3. **Configure Windows Firewall:**
   - Open Windows Defender Firewall
   - Click "Advanced settings"
   - Inbound Rules → New Rule
   - Port → TCP → 8765 → Allow the connection

### Windows (Native)

```powershell
# Install netcat via Chocolatey
choco install netcat

# Use same syntax as Linux
echo '{"type":"file","path":"test.py","line":1}' | nc localhost 8765
```

Or use PowerShell directly:
```powershell
$client = New-Object System.Net.Sockets.TcpClient("localhost", 8765)
$stream = $client.GetStream()
$writer = New-Object System.IO.StreamWriter($stream)
$reader = New-Object System.IO.StreamReader($stream)
$writer.WriteLine('{"type":"file","path":"test.py","line":1}')
$writer.Flush()
$reader.ReadLine()
$client.Close()
```

## Troubleshooting

### Connection refused

1. Ensure your IDE is running with a project open (server starts when project opens)
2. Check plugin is installed: **Settings** → **Plugins** → search "IntelliJ Navigator"
3. Check IDE logs: **Help** → **Show Log in Finder/Explorer**

### Port already in use

```bash
# Check what's using port 8765
lsof -i :8765              # macOS/Linux
netstat -ano | findstr :8765   # Windows
```

### Plugin not loading

Check the IDE log for errors:
- macOS: `~/Library/Logs/JetBrains/<IDE>*/idea.log`
- Linux: `~/.cache/JetBrains/<IDE>*/log/idea.log`
- Windows: `%APPDATA%\JetBrains\<IDE>*\log\idea.log`

## Building from Source

See [CLAUDE.md](CLAUDE.md) for build instructions.
