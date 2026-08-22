# OpenAI Codex Provider Module

`:provider-openai-codex` owns the clean-room adapter for the official OpenAI Codex CLI and its app-server JSON-RPC protocol.

## Dependency rule

The module depends on `:provider-core` and `:agent-core` for provider contracts and shared
cancellation. It must not depend on `:application`, `:ui`, `:desktop`, `:protocol`, `:tools`,
or the `:providers` aggregate.

```text
:application -> :providers -> :provider-openai-codex -> :provider-core
```

## Commands

Run from the repository root:

```bash
./gradlew :provider-openai-codex:build
./gradlew :provider-openai-codex:test
```
