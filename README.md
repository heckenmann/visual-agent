# Visual Agent

<p align="center">
  <img src="modules/ui/src/main/resources/icons/visual-agent.svg" alt="Visual Agent icon" width="160">
</p>

Visual Agent is a Kotlin desktop application. Its goal is to provide the model with as many tools as possible so it can visualize its own output — today that includes a canvas, workspace files, and a todo/sub-agent system; future work will add more rendering and interaction surfaces.

> **Development notice:** This project was written entirely with LLM assistance and is still under active development.
> Expect rapid changes, incomplete features, and rough edges until the project reaches a stable release.

## Download and Run

Download the matching native package from [GitHub Releases](https://github.com/heckenmann/visual-agent/releases). Each package contains the Visual Agent JAR, all runtime dependencies, and a platform-native launcher, so no Java installation is required.

1. macOS: download the package for your processor (`macos-arm64` or `macos-x64`), open the `.dmg`, and move Visual Agent to Applications.
2. Windows x64: run `visual-agent-windows.msi`.
3. Linux x86_64: install `visual-agent-linux-deb.deb` on Debian/Ubuntu or `visual-agent-linux-rpm.rpm` on Fedora/openSUSE/RHEL. Alternatively, make `visual-agent-linux-appimage.AppImage` executable and start it directly; it does not need to be installed.
4. Java 24 option: download the JAR matching your platform (`visual-agent-linux-x64-jar.jar`, `visual-agent-macos-arm64-jar.jar`, `visual-agent-macos-x64-jar.jar`, or `visual-agent-windows-x64-jar.jar`) and run it with `java -jar`. It does not provide native operating-system integration.

The native launcher supplies the Visual Agent name and icon to the operating system. On first launch it creates a local SQLite database under `./data/` and opens the Compose UI.

Supported native release platforms are macOS (Apple Silicon and Intel), Windows x64, and Linux x86_64. The Linux AppImage is portable; the DEB and RPM packages integrate with the system package manager and application menu. A platform-matched executable JAR is additionally available for Java 24 environments.

## Features

- Chat with local (Ollama) or remote (OpenAI-compatible) models, with streaming responses.
- Editable canvas where the model can draw text, shapes, and images.
- Managed workspace files the model can import, read, search, and render (including PDF page previews).
- Todo list and sub-agents that can work on tasks autonomously.
- Per-agent tool configuration, provider profiles, and persisted settings.
- Command palette (`Cmd/Ctrl+K`) and customizable workspace panel layout.
- Icon-only actions with descriptive tooltips throughout the Compose workspace.

See the [use-case catalog](docs/usecases/) for the full list of user-visible functions.

## Prerequisites and Build from Source

See [Setup Guide](docs/setup.md) for prerequisites, build/run commands, Ollama configuration, persistence, and troubleshooting.

```bash
./gradlew build
./gradlew :desktop:run
```

`./gradlew :desktop:run` builds and starts the same native application image used by the release packages. Use `./gradlew :desktop:runDistributable` explicitly when you want to run that image without the `run` alias.

## Gradle Modules

The project uses an acyclic Gradle module graph:

```text
:desktop ──► :ui + :application + :protocol
:application ──► :providers + :tools + :protocol
:providers ──► :provider-standard + :provider-openai-codex
:provider-standard ──► :provider-core + :agent-core
:provider-openai-codex ──► :provider-core + :agent-core
:tools ──► :tool-standard + :tool-javascript
:tool-javascript ──► :tool-standard + :agent-core
:ui ──► :protocol
```

`:application` is the Spring server composition root. `:desktop` is the presentation/lifecycle
host and starts one non-web Spring context from `:application` in local mode. `:ui` is a
protocol-only presentation module; `:providers`, `:provider-openai-codex`, and `:tools` remain server-side modules.

Filesystem ownership follows the same structure:

```text
application/          # :application
modules/ui/           # :ui
modules/agent-core/   # :agent-core
modules/provider-core/ # :provider-core
modules/providers/    # :provider-standard
modules/provider-openai-codex/ # :provider-openai-codex
modules/providers-bundle/ # :providers
modules/tools/        # :tool-standard
modules/tool-javascript/ # :tool-javascript
modules/tools-bundle/ # :tools
```

Run targeted module tasks from the repository root:

```bash
./gradlew :ui:build :ui:test
./gradlew :providers:build
./gradlew :provider-standard:build :provider-standard:test
./gradlew :provider-core:build :provider-core:test
./gradlew :provider-openai-codex:build :provider-openai-codex:test
./gradlew :tools:build
./gradlew :tool-standard:build :tool-standard:test
./gradlew :tool-javascript:build :tool-javascript:test
./gradlew :application:build :application:test
./gradlew :desktop:run
./gradlew :application:runServer
```

See the module READMEs for ownership and migration details: [`:application`](application/README.md), [`:ui`](modules/ui/README.md), [`:provider-standard`](modules/providers/README.md), and [`:provider-openai-codex`](modules/provider-openai-codex/README.md).

## Documentation

- [Architecture](docs/architecture.md) — runtime layers, provider routing, tool system, persistence, in-flight indicator, current constraints
- [Gradle Module Architecture](docs/gradle-module-architecture.md) — module graph, dependency rules, migration sequence, and verification commands
- [API Reference](docs/api.md) — `LLMProvider`, Spring AI integration, tool-calling contracts, activity surface
- [Database Schema](docs/database.md) — SQLite schema, indexes, persistence behavior
- [SubAgents](docs/subagents.md) — autonomous/sub-agent model, tool sets, autonomous loop
- [Compose Migration Audit](docs/compose-migration-audit.md) — per-requirement evidence for the JavaFX to Compose Multiplatform decision
- [Development Conventions](docs/conventions.md) — use-case traceability and documentation rules

## License

MIT License
