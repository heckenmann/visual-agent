# OpenAI Codex Provider Module

`:provider-openai-codex` owns the clean-room adapter for the official OpenAI Codex CLI and its app-server JSON-RPC protocol.

## Dependency rule

The module depends only on `:providers` for provider contracts and the profile-aware adapter SPI. It must not depend on `:application`, `:ui`, `:desktop`, `:protocol`, or `:tools`.

```text
:application -> :provider-openai-codex -> :providers
```

## Commands

Run from the repository root:

```bash
./gradlew :provider-openai-codex:build
./gradlew :provider-openai-codex:test
```
