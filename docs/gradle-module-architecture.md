# Gradle Module Architecture

## Purpose

This document records the incremental Gradle modularisation planned in issues #157, #165, #178, and #65. It establishes dependency directions before source files are relocated.

## Initial module graph

```text
:desktop
 ├── :ui
 ├── :application
 └── :protocol

:application
 ├── :protocol
 ├── :providers
 └── :tools
```

The filesystem layout mirrors the ownership boundary without changing Gradle project paths:

```text
application/          # :application, the composition root
modules/ui/           # :ui, a leaf module
modules/protocol/     # :protocol, versioned gRPC contract
modules/desktop/      # :desktop, Compose/server endpoint host
modules/providers/    # :providers, a leaf module
modules/tools/        # :tools, a leaf module
```

## Version management

`gradle/libs.versions.toml` is the authoritative source for the main Visual Agent build's project version, plugin versions, library versions, and BOM versions. Main-build module scripts must use its `libs` aliases and `libs.versions.visual.agent` instead of inline version literals. The root `verifyCentralizedVersions` check enforces this rule for `:application`, `:ui`, `:providers`, and `:tools`.

The Foojay toolchain resolver is the sole settings-only plugin and remains declared once in `settings.gradle.kts`, where Gradle evaluates it before the catalog is available. It is not applied by, or shared between, main-build modules.

Codex support is consumed as the published `org.springaicommunity.agents:agent-codex` JAR through the root version catalog. No third-party source tree or included build is part of this repository.

When adding a main-build module, apply plugins and dependencies through aliases from the root catalog and set its project version to `libs.versions.visual.agent.get()`; do not copy a version literal into its build script.

`:application` owns standalone Spring bootstrap and server runtime wiring. `:desktop` owns
desktop startup and endpoint selection, and embeds one non-web application context from
`:application` for the local same-JVM deployment.

`:ui` owns Compose desktop UI code and consumes only protocol-owned contracts. All panels use
the same `ApplicationPort` boundary after `ApplicationConnection` reports readiness; the desktop
host owns Spring startup and converts the server-side adapters into the protocol bundle.

`:providers` owns LLM-provider integrations and provider-facing contracts. It has no Gradle project dependency on `:application`, `:ui`, `:tools`, or any future sibling module. Application-specific configuration, tools, persistence, and request context are supplied through narrow provider contracts implemented by `:application`.

`:tools` owns provider-neutral tool contracts, registry infrastructure, lifecycle events, parsing helpers, and tool implementations. It has no Gradle project dependency on `:application`, `:ui`, or `:providers`. Application services are supplied through narrow tool-owned ports implemented and composed by `:application`.

The `:tools` extraction is tracked by issue #165. Concrete tools are Spring-discovered through the `@AgentTool` stereotype, while Application services are supplied through narrow tool-owned ports implemented and composed by `:application`. The module uses Spring as a compile-only dependency; the consuming Application runtime supplies it.

## Dependency rules

- `:application` is the server composition root and may depend on `:protocol`, `:providers`, and `:tools`.
- `:desktop` may depend on `:ui`, `:protocol`, and the server bootstrap artifact.
- `:ui` may depend only on `:protocol`; it must not depend on Spring, `:application`, providers,
  tools, persistence, or application implementation types.
- `:protocol`, `:providers`, and `:tools` must not depend on `:application` or `:ui`.
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
./gradlew :providers:build :providers:test
./gradlew :tools:build :tools:test
./gradlew :application:build :application:test
./gradlew :desktop:run
./gradlew :application:runServer
```

The root quality gate must continue to run the full multi-module verification suite.
