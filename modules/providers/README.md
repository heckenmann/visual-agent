# Standard Provider Module

`:provider-standard` owns the configured-provider router and the built-in Ollama and
OpenAI-compatible adapters. Shared contracts live in `:provider-core`; the complete provider
bundle is exposed by `:providers`.

## Responsibilities

- Provides provider-specific, application-independent implementation slices.
- Declares narrow provider contracts when an integration needs application-supplied behavior.
- Keeps provider implementation independent from the Compose UI and every other Visual Agent module.

## Dependency rule

`:provider-standard` must not have a Gradle project dependency on `:application`, `:ui`, or
the provider aggregate.

```text
:application -> :providers -> :provider-standard -> :provider-core
                         └── :provider-openai-codex -> :provider-core
```

## Commands

Run from the repository root:

```bash
./gradlew :provider-standard:build
./gradlew :provider-standard:test
```
