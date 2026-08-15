# Application Module

`:application` is the Visual Agent Spring server composition root.

## Responsibilities

- Owns the Spring Boot server composition root used by both standalone and embedded desktop startup.
- Exposes application capabilities through the protocol ports consumed by the desktop UI.
- Retains agent, persistence, workspace, canvas, todo, knowledge, orchestration, and configuration logic until those capabilities receive dedicated extraction increments.
- Owns cross-module integration tests and the application JaCoCo verification rule.

The desktop lifecycle host lives in `:desktop`. In local desktop mode it embeds exactly one
non-web Spring context from this module; it does not create a second application server.

## Dependencies

This module owns the server-side dependency direction:

```text
:application -> :providers
:application -> :tools
:application -> :protocol
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
./gradlew :application:runServer
```
