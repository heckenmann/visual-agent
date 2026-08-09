# Gradle Module Architecture

## Purpose

This document records the incremental Gradle modularisation planned in issues #157 and #165. It establishes dependency directions before source files are relocated.

## Initial module graph

```text
:application
 ├── :ui
 ├── :providers
 └── :tools
```

The filesystem layout mirrors the ownership boundary without changing Gradle project paths:

```text
application/          # :application, the composition root
modules/ui/           # :ui, a leaf module
modules/providers/    # :providers, a leaf module
modules/tools/        # :tools, a leaf module
```

## Version management

`gradle/libs.versions.toml` is the authoritative source for the main Visual Agent build's project version, plugin versions, library versions, and BOM versions. Main-build module scripts must use its `libs` aliases and `libs.versions.visual.agent` instead of inline version literals. The root `verifyCentralizedVersions` check enforces this rule for `:application`, `:ui`, `:providers`, and `:tools`.

The Foojay toolchain resolver is the sole settings-only plugin and remains declared once in `settings.gradle.kts`, where Gradle evaluates it before the catalog is available. It is not applied by, or shared between, main-build modules.

`third_party/cokit` is an included build maintained independently. Its `gradle/libs.versions.toml` remains its own authority and is intentionally not coupled to the main build's catalog. The main build consumes only the published `cokit-client` artifact, whose version is defined in the main catalog.

When adding a main-build module, apply plugins and dependencies through aliases from the root catalog and set its project version to `libs.versions.visual.agent.get()`; do not copy a version literal into its build script.

`:application` is the only composition root. It owns desktop startup, Spring bootstrap, runtime wiring, and all logic that has not yet been extracted into a cohesive module.

`:ui` owns Compose desktop UI code and UI-facing contracts. It has no Gradle project dependency on `:application`, `:providers`, or any future sibling module. Application-specific operations are supplied through narrow UI contracts implemented by `:application`.

`:providers` owns LLM-provider integrations and provider-facing contracts. It has no Gradle project dependency on `:application`, `:ui`, `:tools`, or any future sibling module. Application-specific configuration, tools, persistence, and request context are supplied through narrow provider contracts implemented by `:application`.

`:tools` owns provider-neutral tool contracts, registry infrastructure, lifecycle events, parsing helpers, and tool implementations. It has no Gradle project dependency on `:application`, `:ui`, or `:providers`. Application services are supplied through narrow tool-owned ports implemented and composed by `:application`.

The `:tools` extraction is tracked by issue #165. Concrete tools are Spring-discovered through the `@AgentTool` stereotype, while Application services are supplied through narrow tool-owned ports implemented and composed by `:application`. The module uses Spring as a compile-only dependency; the consuming Application runtime supplies it.

## Dependency rules

- `:application` is the parent and may depend on every submodule.
- A submodule must not depend on `:application`.
- A submodule must not depend on any other submodule.
- The dependency graph is strictly one-way: `:application` to its submodules only.
- A submodule declares any contract it needs; `:application` supplies the contract implementation and composes the modules.
- Spring and Compose composition remains in `:application` until a later module owns a complete independent runtime boundary.

## Migration sequence

1. Create the three projects and preserve the existing application runtime in `:application`.
2. Introduce narrow contracts at current dependency seams.
3. Relocate one coherent UI or provider slice at a time with `git mv`, together with its tests and required resources.
4. Verify each new module directly with `:<module>:build` and `:<module>:test` before extracting another slice.
5. Keep all unextracted agent, persistence, workspace, canvas, todo, knowledge, orchestration, and configuration logic in `:application`.
6. Extract tool contracts and infrastructure into `:tools`, then move implementations behind ports one coherent tool family at a time.

## File-move rule

Every source, test, or resource relocation must use `git mv`. Copy/delete moves are prohibited so Git history remains reviewable.

## Verification

The final initial layout must support:

```bash
./gradlew :ui:build :ui:test
./gradlew :providers:build :providers:test
./gradlew :tools:build :tools:test
./gradlew :application:build :application:test
./gradlew :application:run
```

The root quality gate must continue to run the full multi-module verification suite.
