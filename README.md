# IntelliJ Navigator

<a name="intellij-navigator-plugin"></a>

IDE navigation plugins for [AgentTerm](https://github.com/albertwujj/agent-term). Follow an agent's file or symbol references into your code, and quote the IDE's current file and line back into your prompt.

<a name="download"></a>
<a name="plugin-roles"></a>

## Adding it

Download both plugin ZIPs from the same [release](https://github.com/albertwujj/intellij-navigator/releases): `intellij-navigator` and `intellij-navigator-frontend`.

- **Local IDE:** install both in the same IDE.
- **Remote Development:** install the backend on the host IDE and the frontend on the client IDE.

The plugins are tested with PyCharm. See the [installation guide](INSTALL.md) for IDE requirements and installation steps.

<a name="features"></a>
<a name="quick-start"></a>

## Using it

Keep a project open in your IDE while you work in AgentTerm. A read-only editor guard is enabled by default to prevent accidental typing while agents edit files. You can turn it off for direct editing.

See [AgentTerm's IDE guide](https://github.com/albertwujj/agent-term/blob/main/docs/ide.md) for navigation and quoting code locations into your prompt.

<a name="documentation"></a>

## The mechanics

Other clients can use the [JSON-over-TCP API](API.md). For development, see [build and test instructions](CLAUDE.md) and the [local split-mode workflow](LOCAL_SPLIT_MODE.md).

## License

[MIT](LICENSE).
