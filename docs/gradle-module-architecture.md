# Gradle Module Architecture

## Purpose

This document records the incremental Gradle modularisation planned in issues #157, #165, #178, and #65. It establishes dependency directions before source files are relocated.

## Current module graph

```text
:desktop
 ├── :ui
 ├── :application
 └── :protocol

:application
 ├── :protocol
 ├── :providers (provider aggregate)
 └── :tools (tool aggregate)

:providers
 ├── :provider-standard
 └── :provider-openai-codex

:provider-standard
 ├── :provider-core
 └── :agent-core

:provider-openai-codex
 ├── :provider-core
 └── :agent-core

:tools
 ├── :tool-standard
 └── :tool-javascript

:tool-javascript
 ├── :tool-standard
 └── :agent-core
```

The filesystem layout mirrors the ownership boundary without changing Gradle project paths:

```text
application/          # :application, the composition root
modules/ui/           # :ui, a leaf module
modules/protocol/     # :protocol, versioned gRPC contract
modules/desktop/      # :desktop, Compose/server endpoint host
modules/agent-core/   # :agent-core, shared cancellation primitives
modules/provider-core/ # :provider-core, shared provider contracts and catalog
modules/providers/    # :provider-standard, built-in Ollama/OpenAI-compatible adapters
modules/provider-openai-codex/ # :provider-openai-codex, OpenAI Codex CLI adapter
modules/providers-bundle/ # :providers, provider aggregate
modules/tools/        # :tool-standard, built-in tools
modules/tool-javascript/ # :tool-javascript, GraalJS tool
modules/tools-bundle/ # :tools, tool aggregate
```

## Version management

`gradle/libs.versions.toml` is the authoritative source for the main Visual Agent build's project version, plugin versions, library versions, and BOM versions. Main-build module scripts must use its `libs` aliases and `libs.versions.visual.agent` instead of inline version literals. The root `verifyCentralizedVersions` check enforces this rule for every main-build module.

The Foojay toolchain resolver is the sole settings-only plugin and remains declared once in `settings.gradle.kts`, where Gradle evaluates it before the catalog is available. It is not applied by, or shared between, main-build modules.

Codex support is implemented in `:provider-openai-codex` as a clean-room Spring AI adapter over the public Codex app-server JSON-RPC protocol. No Codex connector source tree, copied implementation, or community connector dependency is part of this repository.

When adding a main-build module, apply plugins and dependencies through aliases from the root catalog and set its project version to `libs.versions.visual.agent.get()`; do not copy a version literal into its build script.

`:application` owns standalone Spring bootstrap and server runtime wiring. `:desktop` owns
desktop startup and endpoint selection, and embeds one non-web application context from
`:application` for the local same-JVM deployment.

`:ui` owns Compose desktop UI code and consumes only protocol-owned contracts. All panels use
the same `ApplicationPort` boundary after `ApplicationConnection` reports readiness; the desktop
host owns Spring startup and converts the server-side adapters into the protocol bundle.

`:provider-core` owns provider-facing contracts, provider profiles/catalog models, and shared cancellation primitives. It has no dependency on a concrete provider implementation.

`:provider-standard` owns the configured-provider router and the built-in Ollama and OpenAI-compatible adapters. It depends on `:provider-core` and `:agent-core`.

`:provider-openai-codex` owns the OpenAI Codex CLI/app-server adapter. It depends on the provider contracts in `:provider-core`, not on the provider aggregate. The `:providers` aggregate exposes it transitively together with `:provider-standard`.

`:tool-standard` owns provider-neutral tool contracts, registry infrastructure, lifecycle events, parsing helpers, and the standard tool implementations. It has no Gradle project dependency on `:application`, `:ui`, or providers.

`:tool-javascript` owns the GraalJS sandbox and depends on `:tool-standard` for tool execution. Its hardened workspace contract is implemented by `:application`. The `:tools` aggregate exposes both standard and JavaScript tools transitively.

## Dependency rules

- `:application` is the server composition root and depends on the complete `:providers` and `:tools` aggregates plus `:protocol`.
- `:desktop` may depend on `:ui`, `:protocol`, and the server bootstrap artifact.
- `:ui` may depend only on `:protocol`; it must not depend on Spring, `:application`, providers,
  tools, persistence, or application implementation types.
- `:agent-core`, `:provider-core`, `:provider-standard`, `:provider-openai-codex`, `:providers`, `:tool-standard`, `:tool-javascript`, and `:tools` must not depend on `:application` or `:ui`.
- The dependency graph is strictly one-way from hosts to contracts and implementations.
- A submodule declares any contract it needs; `:application` supplies the contract implementation and composes the modules.
- Spring and Compose composition remains in `:application` until a later module owns a complete independent runtime boundary.

## Migration sequence

1. Create the protocol and desktop host projects and preserve the application runtime in `:application`.
2. Keep all cross-boundary operations in protocol ports and DTOs.
3. Start Spring from `:desktop` while Compose renders the splash, then construct the UI dependency bundle from protocol beans.
4. Verify each module directly with `:<module>:build` and `:<module>:test`.
5. Keep agent, persistence, workspace, canvas, todo, knowledge, orchestration, and configuration logic in `:application`.
6. Keep remote endpoint handshakes explicit and fail-safe; replace the local adapter with a
   complete remote gRPC `ApplicationPort` client only as a separate deployment change.
   The standalone server remains loopback-only when networking is explicitly enabled.

## File-move rule

Every source, test, or resource relocation must use `git mv`. Copy/delete moves are prohibited so Git history remains reviewable.

## Verification

The final initial layout must support:

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

The root quality gate must continue to run the full multi-module verification suite.
