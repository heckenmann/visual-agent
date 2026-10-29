# Visual Agent

<p align="center">
  <img src="modules/ui/src/main/resources/icons/visual-agent.svg" alt="Visual Agent icon" width="160">
</p>

Visual Agent is a Kotlin desktop application. Its goal is to provide the model with as many tools as possible so it can visualize its own output — today that includes a canvas, workspace files, and a todo/sub-agent system; future work will add more rendering and interaction surfaces.

> **Development notice:** This project was written entirely with LLM assistance and is still under active development.
> Expect rapid changes, incomplete features, and rough edges until the project reaches a stable release.

Visual Agent's own source is [MIT-licensed](LICENSE). Dependencies and packaged components retain their
own licenses; see the [dependency and distribution license audit](docs/dependency-license-audit.md)
for the current inventory, outstanding notice obligations, and binary-release limitations.

## Download and Run

Download the matching artifact from [GitHub Releases](https://github.com/heckenmann/visual-agent/releases). Native packages contain the Visual Agent JAR, all runtime dependencies, and a platform-native launcher, so no Java installation is required. The platform-specific JAR downloads require Java 24.

| Operating system | Architecture | Package | Download |
| --- | --- | --- | --- |
| macOS | Apple Silicon | DMG | [visual-agent-macos-arm64.dmg](https://github.com/heckenmann/visual-agent/releases/latest/download/visual-agent-macos-arm64.dmg) |
| macOS | Apple Silicon | JAR (Java 24) | [visual-agent-macos-arm64-jar.jar](https://github.com/heckenmann/visual-agent/releases/latest/download/visual-agent-macos-arm64-jar.jar) |
| macOS | Intel | DMG | [visual-agent-macos-x64.dmg](https://github.com/heckenmann/visual-agent/releases/latest/download/visual-agent-macos-x64.dmg) |
| macOS | Intel | JAR (Java 24) | [visual-agent-macos-x64-jar.jar](https://github.com/heckenmann/visual-agent/releases/latest/download/visual-agent-macos-x64-jar.jar) |
| Windows | x64 | MSI | [visual-agent-windows.msi](https://github.com/heckenmann/visual-agent/releases/latest/download/visual-agent-windows.msi) |
| Windows | x64 | JAR (Java 24) | [visual-agent-windows-x64-jar.jar](https://github.com/heckenmann/visual-agent/releases/latest/download/visual-agent-windows-x64-jar.jar) |
| Debian / Ubuntu Linux | x86_64 | DEB | [visual-agent-linux-deb.deb](https://github.com/heckenmann/visual-agent/releases/latest/download/visual-agent-linux-deb.deb) |
| Fedora / openSUSE / RHEL Linux | x86_64 | RPM | [visual-agent-linux-rpm.rpm](https://github.com/heckenmann/visual-agent/releases/latest/download/visual-agent-linux-rpm.rpm) |
| Linux | x86_64 | AppImage | [visual-agent-linux-appimage.AppImage](https://github.com/heckenmann/visual-agent/releases/latest/download/visual-agent-linux-appimage.AppImage) |
| Linux | x86_64 | JAR (Java 24) | [visual-agent-linux-x64-jar.jar](https://github.com/heckenmann/visual-agent/releases/latest/download/visual-agent-linux-x64-jar.jar) |

Open a macOS disk image and move Visual Agent to Applications. Run the Windows installer directly.
Install Linux DEB/RPM files with the system package manager. Make the AppImage executable and start
it directly; it does not need to be installed. Run a platform-matched JAR with `java -jar`; it does
not provide native operating-system integration.

The native launcher supplies the Visual Agent name and icon to the operating system. On first
launch the server creates its H2 database in the platform-specific per-user server data
directory and the Compose UI loads its client-local server bootstrap configuration before opening
the selected connection.

Supported native release platforms are macOS (Apple Silicon and Intel), Windows x64, and Linux x86_64. The Linux AppImage is portable; the DEB and RPM packages integrate with the system package manager and application menu. A platform-matched executable JAR is additionally available for Java 24 environments.

Publishing a GitHub release builds its tag with the corresponding Gradle application version automatically
(for example, `v1.2.3-beta.1` becomes `1.2.3-beta.1`), without changing the checked-in version catalog.
Tags must be Semantic Versions, with or without a `v` prefix (`v` is recommended).
Native installer versions use the numeric `1.2.3` core; application metadata and JARs retain the full version.
Development, compilation, tests, CI, and packaging use JDK 27, including its corrected `jpackage`
and bundled native runtime. Bytecode/API targets stay at Java 24 for standalone JAR compatibility.
Assets are attached only after package smoke tests pass. Creating a tag alone does not trigger publication.
Local builds and manually dispatched smoke tests use the catalog's default version.
See [Release Versions](docs/setup.md#release-versions) for examples, installer limits, and local reproduction.

## Features

- Chat with Ollama, OpenAI-compatible, Codex CLI, or CLI-independent Codex Direct providers, with streaming responses.
- Editable canvas where the model can draw text, shapes, and images.
- Managed workspace files the model can import, read, search, and render (including PDF page previews).
- Todo list and sub-agents that can work on tasks autonomously.
- Per-agent tool configuration, provider profiles, and persisted settings.
- Searchable model selection directly in the Conversation composer, with favorites
  and provider grouping. Global Settings separates Appearance, Providers, Agent,
  and Updates; Conversation settings contains only model context and message behavior.
- Self-model switching through `model_selection`, with explicit grants required for sub-agents.
- Searchable reusable Markdown skills that the main agent can save and retrieve explicitly,
  with a user-facing Skills panel and database-backed search.
- Command palette (`Cmd/Ctrl+K`) and customizable workspace panel layout.
- Icon-only actions with descriptive tooltips throughout the Compose workspace.

See the [use-case catalog](docs/usecases/) for the full list of user-visible functions.

## Prerequisites and Build from Source

See [Setup Guide](docs/setup.md) for prerequisites, build/run commands, Ollama configuration, persistence, and troubleshooting.
In a fresh clone, generate the intentionally unversioned Gradle Wrapper JAR as described there before invoking `./gradlew`.

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
:providers ──► :provider-standard + :provider-openai-codex + :provider-codex-direct
:provider-standard ──► :provider-core + :agent-core
:provider-openai-codex ──► :provider-core + :agent-core
:provider-codex-direct ──► :provider-core + :provider-standard
:tools ──► :tool-standard + :tool-javascript
:tool-javascript ──► :tool-standard + :agent-core
:ui ──► :protocol
:protocol ──► :task-core + :model-core
:provider-core ──► :agent-core + :model-core
```

`:application` is the Spring server composition root. `:desktop` is the presentation/lifecycle
host and starts one non-web Spring context from `:application` in local mode. `:ui` is a
protocol-only presentation module; `:providers`, `:provider-openai-codex`, and `:tools` remain server-side modules.

Filesystem ownership follows the same structure:

```text
application/          # :application
modules/ui/           # :ui
modules/desktop/      # :desktop, startup and native packaging
modules/protocol/     # :protocol, shared client/server contracts
modules/task-core/    # :task-core, shared task models
modules/agent-core/   # :agent-core
modules/model-core/   # :model-core, pure capability evidence and override resolution
modules/provider-core/ # :provider-core
modules/providers/    # :provider-standard
modules/provider-openai-codex/ # :provider-openai-codex
modules/provider-codex-direct/ # CLI-independent Responses and explicit OAuth/API-key authentication
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
- [Database Schema](docs/database.md) — H2 schema, indexes, persistence behavior
- [SubAgents](docs/subagents.md) — autonomous/sub-agent model, tool sets, autonomous loop
- [Compose Migration Audit](docs/compose-migration-audit.md) — per-requirement evidence for the JavaFX to Compose Multiplatform decision
- [Development Conventions](docs/conventions.md) — use-case traceability and documentation rules
- [Codex Direct Provider](docs/codex-direct-provider.md) — CLI-independent Responses adapter and authentication
- [Release Package Verification](docs/usecases/uc_0000146_smoke_test_packaged_release_artifacts.md) — versioning, package matrix, smoke tests, and publication

## License

MIT License
