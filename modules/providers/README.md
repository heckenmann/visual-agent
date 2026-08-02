# Providers Module

`:providers` owns LLM-provider integrations and provider-facing contracts.

## Responsibilities

- Provides provider-specific, application-independent implementation slices.
- Declares narrow provider contracts when an integration needs application-supplied behavior.
- Keeps provider implementation independent from the Compose UI and every other Visual Agent module.

## Dependency rule

`:providers` is a leaf module. It must not have a Gradle project dependency on `:application`, `:ui`, or any future sibling module.

```text
:application -> :providers
```

## Commands

Run from the repository root:

```bash
./gradlew :providers:build
./gradlew :providers:test
```
