# Local Split Mode

This document separates the local split-mode workflow into:

- one-time bootstrap
- repeated steps after frontend code changes

It is for the current two-plugin layout:

- `intellij-navigator` on the host/backend side
- `intellij-navigator-frontend` on the client/frontend side

## Why This Exists

Local split-mode testing uses two different places:

- the backend IDE sandbox under `build/idea-sandbox/...`
- the real JetBrains Client profile under `~/Library/Application Support/JetBrains/JetBrainsClient*`

The frontend plugin must be restaged into the real JetBrains Client profile after frontend code changes. Copying it only into the sandbox is not enough.

## One-Time Bootstrap

Run once per machine or once per clean local environment:

```bash
bash scripts/split-mode-bootstrap.sh
```

What it does once:

- chooses a stable `GRADLE_USER_HOME` at `.gradle-local/split-mode`
- warms the shared Gradle wrapper state for both the root plugin and `frontend-plugin`
- checks whether a JetBrains Client profile already exists

If no JetBrains Client profile exists yet, do this once:

```bash
bash scripts/split-mode-run.sh /absolute/path/to/project
```

The first launch creates the JetBrains Client profile. It may not have the frontend plugin installed yet. After that first launch, run:

```bash
bash scripts/split-mode-refresh-frontend.sh
```

## Repeat After Frontend Code Changes

1. Run frontend tests:

```bash
bash scripts/split-mode-frontend-test.sh
```

You can pass narrower Gradle arguments if needed:

```bash
bash scripts/split-mode-frontend-test.sh test --tests 'com.claudecode.navigator.frontend.FrontendEditorModelResolverTest'
```

2. Rebuild and reinstall the frontend plugin into the real JetBrains Client profile:

```bash
bash scripts/split-mode-refresh-frontend.sh
```

3. Restart split mode:

```bash
bash scripts/split-mode-run.sh /absolute/path/to/project
```

## When Reinstall Is Required

- Unit tests and compilation: no reinstall needed
- Live split-mode runtime testing: reinstall and restart required after frontend code changes

## Verifying The Frontend Client

With split mode running and a project open:

```bash
printf '{"action":"caret_diagnostics"}\n' | nc -w 2 localhost 8766
```

If that fails, check the JetBrains Client log:

```bash
ls -td ~/Library/Logs/JetBrains/JetBrainsClient*/* | head -1
```

## English Menus In JetBrains Client

If split mode opens with translated menus, the JetBrains Client profile may still have language-pack plugins installed under:

```text
~/Library/Application Support/JetBrains/JetBrainsClient241.14494.241/plugins
```

For English menus, disable or remove client language packs such as:

- `ko.241.271`
- `ja.241.271`
- `zh.241.271`

They can be moved aside into a sibling directory such as `plugins-disabled/` and restored later if needed.

## Current Fidelity Limits

Local split mode is useful for:

- frontend startup/install verification
- `8766` diagnostics
- wrapper-level probing such as `FrontendDefaultFileEditor`

Windows Remote Dev is still the source of truth for the exact `Commit` diff UI shape.
