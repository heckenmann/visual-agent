# Visual Agent

<p align="center">
  <img src="src/main/resources/icons/visual-agent.svg" alt="Visual Agent icon" width="160">
</p>

Visual Agent is a Kotlin desktop application. Its goal is to provide the model with as many tools as possible so it can visualize its own output — today that includes a canvas, workspace files, and a todo/sub-agent system; future work will add more rendering and interaction surfaces.

> **Development notice:** This project was written entirely with LLM assistance and is still under active development.
> Expect rapid changes, incomplete features, and rough edges until the project reaches a stable release.

## Download and Run

Each successful build on `master` publishes an executable JAR to GitHub Packages. You need a GitHub personal access token with `read:packages` scope to download it.

1. Download the latest `visual-agent-0.1.0-master-*.jar` from the [GitHub Packages registry](https://github.com/heckenmann/visual-agent/pkgs/maven/visual-agent).
2. Run it with Java 21 or later:

   ```bash
   java -jar visual-agent-0.1.0-master-<version>.jar
   ```

   Visual Agent needs a desktop environment (it won't run headless).

3. On first launch it creates a local SQLite database under `./data/` and opens the Compose UI.

## Features

- Chat with local (Ollama) or remote (OpenAI-compatible) models, with streaming responses.
- Editable canvas where the model can draw text, shapes, and images.
- Managed workspace files the model can import, read, search, and render (including PDF page previews).
- Todo list and sub-agents that can work on tasks autonomously.
- Per-agent tool configuration, provider profiles, and persisted settings.
- Command palette (`Cmd/Ctrl+K`) and customizable workspace panel layout.

See the [use-case catalog](docs/usecases/) for the full list of user-visible functions.

## Prerequisites and Build from Source

See [Setup Guide](docs/setup.md) for prerequisites, build/run commands, Ollama configuration, persistence, and troubleshooting.

```bash
./gradlew build
./gradlew run
```

## Documentation

- [Architecture](docs/architecture.md) — runtime layers, provider routing, tool system, persistence, in-flight indicator, current constraints
- [API Reference](docs/api.md) — `LLMProvider`, Spring AI integration, tool-calling contracts, activity surface
- [Database Schema](docs/database.md) — SQLite schema, indexes, persistence behavior
- [SubAgents](docs/subagents.md) — autonomous/sub-agent model, tool sets, autonomous loop
- [Compose Migration Audit](docs/compose-migration-audit.md) — per-requirement evidence for the JavaFX to Compose Multiplatform decision
- [Development Conventions](docs/conventions.md) — use-case traceability and documentation rules

## License

MIT License