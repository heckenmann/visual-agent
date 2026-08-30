# Protocol Module

`:protocol` defines the stable boundary between the Compose presentation process and the
application server. It contains port interfaces, serializable request/response models, protocol
errors, protocol-version negotiation, and the gRPC schema.

## Responsibilities

- Defines ports for conversation, todos, settings, providers, workspace files, canvas, activity,
  lifecycle, and application control.
- Owns `visual_agent_session.proto` and generated gRPC bindings.
- Remains implementation-neutral: it does not depend on Spring, Compose, providers, or tools.

Both `:ui` and `:application` depend on this module. Any new UI-to-server capability must be
represented here before either side implements it.

## Commands

Run from the repository root:

```bash
./gradlew :protocol:build
./gradlew :protocol:test
```
