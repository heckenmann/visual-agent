# Gradle Module Architecture

## Purpose

This document records the incremental Gradle modularisation planned in issue #157. It establishes dependency directions before source files are relocated.

## Initial module graph

```text
:application
 ├── :ui
 └── :providers
```

The filesystem layout mirrors the ownership boundary without changing Gradle project paths:

```text
application/          # :application, the composition root
modules/ui/           # :ui, a leaf module
modules/providers/    # :providers, a leaf module
```

`:application` is the only composition root. It owns desktop startup, Spring bootstrap, runtime wiring, and all logic that has not yet been extracted into a cohesive module.

`:ui` owns Compose desktop UI code and UI-facing contracts. It has no Gradle project dependency on `:application`, `:providers`, or any future sibling module. Application-specific operations are supplied through narrow UI contracts implemented by `:application`.

`:providers` owns LLM-provider integrations and provider-facing contracts. It has no Gradle project dependency on `:application`, `:ui`, or any future sibling module. Application-specific configuration, tools, persistence, and request context are supplied through narrow provider contracts implemented by `:application`.

No additional feature or infrastructure module is introduced in the initial migration.

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

## File-move rule

Every source, test, or resource relocation must use `git mv`. Copy/delete moves are prohibited so Git history remains reviewable.

## Verification

The final initial layout must support:

```bash
./gradlew :ui:build :ui:test
./gradlew :providers:build :providers:test
./gradlew :application:build :application:test
./gradlew :application:run
```

The root quality gate must continue to run the full multi-module verification suite.
