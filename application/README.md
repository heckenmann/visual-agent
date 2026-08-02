# Application Module

`:application` is the Visual Agent composition root.

## Responsibilities

- Starts the Compose desktop application and Spring Boot context.
- Wires the UI and provider modules into the application runtime.
- Retains agent, persistence, workspace, canvas, todo, knowledge, orchestration, and configuration logic until those capabilities receive dedicated extraction increments.
- Owns cross-module integration tests and the application JaCoCo verification rule.

## Dependencies

This is the only module allowed to depend on Visual Agent submodules:

```text
:application -> :ui
:application -> :providers
```

## Source layout

Application source, resources, and cross-module integration tests live under this module:

```text
application/src/main/
application/src/test/
```

## Commands

Run from the repository root:

```bash
./gradlew :application:build
./gradlew :application:test
./gradlew :application:run
```
