# Security

## Reporting a vulnerability

Report vulnerabilities through [GitHub's private form](https://github.com/albertwujj/intellij-navigator/security/advisories/new); keep details out of public issues. Include the plugin and IDE versions, the potential impact, and steps to reproduce the issue. Use a small example project instead of private source code or credentials.

## Supported versions

Security fixes land on `main` and are published in a new paired plugin release. Use the latest [release](https://github.com/albertwujj/intellij-navigator/releases/latest) and update both plugins together; older releases are not maintained. Restart the affected IDE host and client after updating. Keep the IDE and its bundled runtime updated within the plugins' declared compatibility range.

## Local access boundary

The backend listens on `127.0.0.1:8765`; the frontend listens on `127.0.0.1:8766`. They accept newline-delimited JSON over TCP, without authentication or encryption. Local processes that can reach those ports can navigate the IDE and read file paths, caret positions, and, in some frontend responses, nearby code used to resolve a diff location.

Run them on a machine whose users and processes you trust. Loopback binding keeps the listeners off network interfaces; it does not isolate them from other users on the same host. For remote development, forward only the required port through an authenticated channel and keep both ends bound to loopback. Do not expose these ports through a public tunnel or broad firewall rule.

Each server accepts at most 16 active connections. Requests must be UTF-8 JSON objects terminated by a newline, fit within 64 KiB, and arrive within five seconds. Non-JSON handshakes, oversized requests, and incomplete or timed-out requests are rejected. Sockets close when the project or plugin shuts down.

## Diagnostics and logs

Object exploration and extended frontend diagnostics are disabled by default. They require `-Dintellij.navigator.diagnostics=true` in the IDE/client VM options and a restart. Enable them only while investigating a trusted project, then remove the option. These actions can inspect IDE object state and invoke object methods; see [optional diagnostics](DIAGNOSTICS.md).

Raw navigation requests and responses are not logged. IDE logs can still contain file paths, project names, and internal errors; review logs before sharing them. The read-only editor guard prevents accidental typing, and is not a security boundary against plugins, local clients, or other processes.

## Automated checks

CodeQL and tests run against both separately built plugins. Gradle dependency submission feeds GitHub's vulnerability and malware alerts; secret scanning and push protection check for supported secret patterns. These checks do not cover every vulnerability or verify the security of the user's IDE installation.

## Build dependency review

The IDE test framework uses Jackson; both builds constrain its test dependencies to a patched Jackson BOM. These libraries are not included in the distributed plugin ZIPs.

[GHSA-r937-wjx7-w2jp](https://github.com/advisories/GHSA-r937-wjx7-w2jp) affects Kotlin's KAPT incremental annotation-processing cache. Neither build applies KAPT or runs annotation processors, so that vulnerable code path is unused. The [upstream fix](https://github.com/JetBrains/kotlin/commit/bf51df665b458fda7c3eaf436c4d88dc119d7ec6) is confined to KAPT cache deserialization. Reassess this finding before adding KAPT or annotation processors; do not treat this disposition as a general exemption for Kotlin advisories.
