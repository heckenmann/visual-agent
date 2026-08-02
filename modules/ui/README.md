# UI Module

`:ui` owns reusable Compose desktop UI code and UI-facing contracts.

## Responsibilities

- Provides Compose components that do not require application or provider implementation details.
- Declares narrow UI contracts when a component needs application-provided behavior.
- Keeps UI implementation independent from all other Visual Agent modules.

## Dependency rule

`:ui` is a leaf module. It must not have a Gradle project dependency on `:application`, `:providers`, or any future sibling module.

```text
:application -> :ui
```

## Commands

Run from the repository root:

```bash
./gradlew :ui:build
./gradlew :ui:test
```
