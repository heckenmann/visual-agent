# UI Module

`:ui` owns reusable Compose desktop UI code. Every panel communicates through protocol-owned
ports supplied by the desktop host.

## Responsibilities

- Provides Compose components that do not require application or provider implementation details.
- Declares narrow UI contracts when a component needs application-provided behavior.
- Keeps UI implementation independent from all other Visual Agent modules.

## Dependency rule

`:ui` is a leaf module that depends only on `:protocol`. Spring startup, application adapters,
and the splash lifecycle belong to `:desktop`.

```text
:desktop -> :ui -> :protocol
:application -> :protocol
```

## Commands

Run from the repository root:

```bash
./gradlew :ui:build
./gradlew :ui:test
```
