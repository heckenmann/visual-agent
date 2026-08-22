# Providers Module

`:providers` owns the provider contracts, the configured-provider router, and the built-in
Ollama and OpenAI-compatible adapters. Dedicated provider integrations live in their own
modules, such as `:provider-openai-codex`.

## Responsibilities

- Provides provider-specific, application-independent implementation slices.
- Declares narrow provider contracts when an integration needs application-supplied behavior.
- Keeps provider implementation independent from the Compose UI and every other Visual Agent module.

## Dependency rule

`:providers` is a leaf module. It must not have a Gradle project dependency on `:application`, `:ui`, or any future sibling module.

```text
:application -> :provider-openai-codex -> :providers
:application -> :providers
```

## Commands

Run from the repository root:

```bash
./gradlew :providers:build
./gradlew :providers:test
```
