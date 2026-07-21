# IntelliJ Navigator Plugin - API Reference

TCP protocol for integrating with the IntelliJ Navigator plugin.

## Connection

Two TCP servers work together:

| Server | Port | Purpose |
|--------|------|---------|
| **Backend** | 8765 | Resolve files/symbols, open or activate files, move caret |
| **Frontend** | 8766 | Scroll the visible editor and report the visible caret |

- **Protocol:** TCP
- **Format:** Newline-delimited JSON

### Request flow

Send a request to the backend. Check the response `status`:

- **Explicit navigation** (file request with `line` or `matchText`, or any symbol/text request)
  - **`"ok"`** — single match, file opened, caret moved. Response includes `file` and `line`.
    Forward them in a scroll request to the frontend:
  ```
  1. backend (8765):  {"type":"file","path":"foo.py","line":42}  →  {"status":"ok","file":"/project/foo.py","line":42}
  2. frontend (8766): {"action":"scroll","file":"/project/foo.py","line":42,"column":0}  →  {"status":"ok"}
  ```

- **State-preserving file activation** (file request with neither `line` nor `matchText`)
  - **`"ok"`** — single match, file activated without forcing a new caret position.
    Response includes `file` but omits `line` and `column`. Do not send a frontend scroll request.
  ```
  backend (8765): {"type":"file","path":"foo.py"} → {"status":"ok","file":"/project/foo.py"}
  ```

- **`"multiple"`** — multiple matches, selector popup shown in IDE.
  - For navigate requests, the user selection navigates and scrolls automatically.
  - For activate requests, the user selection activates the chosen file without forcing caret/scroll state.

- **`"error"`** — no matches found. No further action.

The two-step flow for `"ok"` is required for remote development (WSL/Gateway) where
the backend runs headlessly and cannot scroll the editor. The frontend plugin runs on
the thin client where scroll APIs work. File activation does not need that second step:
the backend selects the file and IntelliJ restores the file's remembered editor state.

For current caret lookup, prefer the frontend server. In remote development the frontend
owns the visible editor state, so `{"action":"caret"}` on port `8766` is the reliable
source for visible caret state used by clients such as AgentTerm. The backend
`{"type":"caret"}` request remains available for compatibility and local tooling.

## Request Types

### File — open a file, or activate it while preserving editor state

```json
{"type":"file","path":"foo/bar.py","line":42}
{"type":"file","path":"foo/bar.py","matchText":"def process():"}
{"type":"file","path":"foo/bar.py"}
```

- **line** (optional): 1-indexed line number.
- **matchText** (optional): expected trimmed line content.
  - With **line + matchText**: validates the line; spirals ±200 if mismatch (`"text_moved"`).
  - With **matchText only** (no line): searches the file top-to-bottom for the first match.
- **no line + no matchText**: opens/selects the file without forcing a new caret location. Do not
  follow that response with a frontend scroll request.

Data class: `FileRequest` in `src/main/kotlin/com/claudecode/navigator/model/NavigationRequest.kt`

### Symbol — navigate to a class, function, method, or variable by name

```json
{"type":"symbol","name":"MyClass.method","fileHint":"models.py"}
```

Data class: `SymbolRequest` in `src/main/kotlin/com/claudecode/navigator/model/NavigationRequest.kt`

### Text — search file contents for a code snippet

```json
{"type":"text","text":"def process():","fileHint":"app.py"}
```

Data class: `TextRequest` in `src/main/kotlin/com/claudecode/navigator/model/NavigationRequest.kt`

### Caret — get current file and line number at the caret

```json
{"type":"caret"}
```

Returns the file path and 1-indexed line number of the caret in the active editor.

- **Regular editor**: returns the file and caret line directly.
- **Unified diff view**: maps the caret position to the corresponding line in the
  current (right-side) version of the file. If the caret is on a deleted line,
  returns the nearest line that exists in the current version.

Response: `{"status":"ok","file":"/project/foo.py","line":42}` or
`{"status":"error","message":"no active editor"}` when no editor is focused.

Data class: `CaretRequest` in `src/main/kotlin/com/claudecode/navigator/model/NavigationRequest.kt`

### Resolve file — canonicalize a file hint and optional diff anchor texts

```json
{"type":"resolve_file","path":"foo/bar.py"}
{"type":"resolve_file","path":"gradle.properties","matchText":"pluginVersion = 1.0.1"}
{"type":"resolve_file","path":"gradle.properties","matchText":"pluginVersion = 1.0.1","matchTextCandidates":["pluginVersion = 1.0.1","pluginVersion = 1.0.5","pluginName = IntelliJ Navigator Frontend"]}
```

Use this when the client has a file hint but needs the backend to return the canonical
project file and, optionally, the authoritative line number.

- **path**: absolute path, project-relative path, or basename-style file hint
- **matchText** (optional): preferred trimmed line text anchor
- **matchTextCandidates** (optional): ordered fallback anchors gathered around the caret

This request is especially important for split-mode diff tabs. The frontend may know only
a basename-style file hint plus nearby unified diff lines. The backend uses the ordered
anchors to choose the best matching file first, then the best matching real line in that file.

Response: `{"status":"ok","file":"/project/foo.py","relativePath":"foo.py","line":42,"column":0}`,
`{"status":"ok","file":"/project/foo.py","relativePath":"foo.py"}`, or an error such as
`{"status":"error","message":"multiple files match: foo.py","count":2}`.

Data class: `ResolveFileRequest` in `src/main/kotlin/com/claudecode/navigator/model/NavigationRequest.kt`

### Frontend caret — get the visible file and line from the client IDE (port 8766)

```json
{"action":"caret"}
```

Returns the visible file path hint from the client IDE.

- **Regular editor**: returns the file, line, and column directly from the selected editor.
- **Split-mode / frontend diff view**: may return a basename or absolute file hint and omit
  `line`. In that case it returns `matchText` plus ordered `matchTextCandidates` from nearby
  unified diff lines so the backend can resolve the real file and real line through
  `{"type":"resolve_file", ...}`.
- **Unified diff view in a local IDE**: may still return the mapped right-side line directly.

Response examples:

```json
{"status":"ok","file":"/project/foo.py","line":42,"column":3}
{"status":"ok","file":"gradle.properties","column":0,"matchText":"pluginVersion = 1.0.1","matchTextCandidates":["pluginVersion = 1.0.1","pluginVersion = 1.0.5","pluginName = IntelliJ Navigator Frontend"]}
{"status":"error","message":"no active editor"}
```

Data classes:
- `CaretRequest` in `frontend-plugin/src/main/kotlin/com/claudecode/navigator/frontend/ScrollServer.kt`
- `CaretResponse` in `frontend-plugin/src/main/kotlin/com/claudecode/navigator/frontend/ScrollServer.kt`

### Scroll — scroll the frontend editor to the caret (port 8766)

Send only after an explicit navigation request returned backend `"ok"`.
Forward the `file` and `line` from that response.

```json
{"action":"scroll","file":"/project/foo.py","line":42,"column":0}
```

Data class: `ScrollRequest` in `frontend-plugin/src/main/kotlin/com/claudecode/navigator/frontend/ScrollServer.kt`

## Responses

### Backend (port 8765)

```json
{"status":"ok","file":"/project/foo.py","relativePath":"foo.py","line":42,"column":0}
{"status":"ok","file":"/project/foo.py","relativePath":"foo.py"}
{"status":"multiple","count":3}
{"status":"error","message":"Not found"}
```

`relativePath` is optional. When the resolved file is under a project content root, it is returned relative to that root for display-oriented clients such as AgentTerm caret insertion.

Data class: `NavigationResponse` in `src/main/kotlin/com/claudecode/navigator/model/NavigationRequest.kt`

### Frontend (port 8766)

```json
{"status":"ok"}
{"status":"file_too_short"}
{"status":"no_editor"}
{"status":"ok","file":"/project/foo.py","line":42,"column":3}
{"status":"error","message":"..."}
```

Data classes:
- `ScrollResponse` in `frontend-plugin/src/main/kotlin/com/claudecode/navigator/frontend/ScrollServer.kt`
- `CaretResponse` in `frontend-plugin/src/main/kotlin/com/claudecode/navigator/frontend/ScrollServer.kt`

---

## Client Example (Python)

```python
import socket
import json

BACKEND_PORT = 8765
FRONTEND_PORT = 8766

def send(request: dict, host="localhost", port=BACKEND_PORT) -> dict:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((host, port))
        s.sendall((json.dumps(request) + "\n").encode())
        response = s.recv(1024).decode()
        return json.loads(response)

def should_scroll(response: dict) -> bool:
    return response.get("status") == "ok" and response.get("line") is not None

def navigate(request: dict, host="localhost") -> dict:
    """Send a request to the backend, then scroll only when coordinates are returned."""
    result = send(request, host, BACKEND_PORT)
    if should_scroll(result):
        send({
            "action": "scroll",
            "file": result["file"],
            "line": result["line"],
            "column": result.get("column", 0),
        }, host, FRONTEND_PORT)
    return result

# Examples
navigate({"type": "file", "path": "foo.py", "line": 10})
navigate({"type": "file", "path": "foo.py"})
navigate({"type": "symbol", "name": "MyClass.method"})
navigate({"type": "symbol", "name": "MyClass", "fileHint": "models.py"})
navigate({"type": "text", "text": "def main():"})
navigate({"type": "text", "text": "def main():", "fileHint": "app.py"})
```
