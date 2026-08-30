# Provider Core

`:provider-core` contains provider contracts, profile/catalog models, provider settings ports,
and shared provider error handling. Concrete adapters depend on this module; the aggregate
`:providers` combines those adapters for application consumers.

## Responsibilities

- Defines `LLMProvider`, stream and vision response models, plus provider capabilities.
- Owns catalog/profile models, merge rules, environment credential ports, and normalized errors.
- Keeps provider contracts independent from Spring composition, the UI, and concrete protocols.

## Commands

Run from the repository root:

```bash
./gradlew :provider-core:build
./gradlew :provider-core:test
```
