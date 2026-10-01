# Optional diagnostics

Use these checks when IDE navigation fails or when developing another client. AgentTerm opens TCP connections directly, and the plugins use Java sockets. Neither needs netcat.

## Where to run the checks

| Plugin | Default port | Runs in |
|---|---|---|
| Backend | `8765` | The IDE that owns the project |
| Frontend | `8766` | The IDE or client displaying the editor |

For a local IDE, both plugins run together. For Remote Development, test the backend on the host and the frontend on the client. The commands below use `127.0.0.1`, so they test the endpoint reachable from the machine where you run them.

On Windows, start with Windows PowerShell: AgentTerm's desktop process runs on Windows even when the agents run in WSL.

## Manual requests

Open a project and a source file in the IDE before checking its current location. A JSON response confirms the plugin is reachable; an error about the active editor means the connection worked but the requested editor state was unavailable.

### macOS or Linux with netcat

If `nc` is available, use it for these optional checks:

```bash
# Backend: current file and caret
printf '{"type":"caret"}\n' | nc -w 3 127.0.0.1 8765

# Frontend: visible file and caret
printf '{"action":"caret"}\n' | nc -w 3 127.0.0.1 8766
```

To test file navigation, replace the path with a file in the open project:

```bash
printf '{"type":"file","path":"path/in/your/project.py","line":1}\n' | nc -w 3 127.0.0.1 8765
```

For an `ok` response containing `file` and `line`, forward those values to the frontend to scroll the visible editor. See the [API request flow](API.md#request-flow) for that second request and other navigation examples.

### Windows PowerShell

This uses the built-in .NET socket client:

```powershell
function Invoke-NavigatorCheck {
    param([int]$Port, [string]$Request)

    $client = [System.Net.Sockets.TcpClient]::new('127.0.0.1', $Port)
    try {
        $stream = $client.GetStream()
        $stream.ReadTimeout = 5000
        $stream.WriteTimeout = 5000
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Request + "`n")
        $stream.Write($bytes, 0, $bytes.Length)
        $reader = [System.IO.StreamReader]::new($stream)
        $reader.ReadLine()
    } finally {
        $client.Dispose()
    }
}

Invoke-NavigatorCheck -Port 8765 -Request '{"type":"caret"}'
Invoke-NavigatorCheck -Port 8766 -Request '{"action":"caret"}'
```

### Checks from WSL

A check started inside WSL can take a different network path from AgentTerm's Windows process. Test from Windows PowerShell first. Both plugins listen only on `127.0.0.1`. Windows-to-WSL localhost forwarding or mirrored networking may supply the local path; a WSL NAT host address alone cannot reach a Windows listener bound to loopback. If a remote setup requires forwarding, use an authenticated tunnel bound to loopback rather than exposing the plugin on a network interface. See [Microsoft's WSL networking guide](https://learn.microsoft.com/en-us/windows/wsl/networking).

## Extended frontend diagnostics

`caret_diagnostics`, `explore_object`, and `diff_probe` are development tools, disabled by default. Enable them temporarily with `-Dintellij.navigator.diagnostics=true` in the VM options of the IDE/client running the frontend plugin, then restart it. They can inspect IDE object state and invoke object methods; use them only with a trusted project and local client. Remove the option and restart after the investigation. Normal navigation and caret quoting do not require it.

## Troubleshooting

### Connection refused or no reply

Check that the project is open and the correct plugin is installed on each side. Inspect the IDE log for plugin startup or socket errors. In Remote Development, a successful check on the backend host alone does not establish that AgentTerm can reach it from the client machine.

### Port already in use

Only one listener can own each default port on a machine. Another IDE or project may already be using it.

With `lsof` available on macOS or Linux:

```bash
lsof -nP -iTCP:8765 -iTCP:8766 -sTCP:LISTEN
```

In Windows PowerShell:

```powershell
netstat -ano | Select-String ':8765\s|:8766\s'
```

Check the listening process before closing an IDE or changing the setup.

### Plugin not loading

Open the IDE log through **Help → Show Log in Finder/Explorer**. Look for missing dependencies, an incompatible IDE build, or startup exceptions. The [installation requirements](INSTALL.md#requirements) list the plugin dependencies and declared build range.

[Back to installation](INSTALL.md).
