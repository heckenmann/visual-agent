# Provider Bundle

`:providers` is the complete provider aggregator. Consumers that need the standard provider
set bind only this module; it exposes `:provider-standard` and `:provider-openai-codex`
transitively.

The aggregate contains no provider implementation of its own. Concrete providers depend on
`:provider-core` and remain independent of the application and UI modules.

Use this module from server-side composition instead of selecting concrete provider projects
individually. It is deliberately a dependency-only Java library.

## Commands

Run from the repository root:

```bash
./gradlew :providers:build
```
