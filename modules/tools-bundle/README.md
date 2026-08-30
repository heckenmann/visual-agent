# Tool Bundle

`:tools` is the complete model-tool aggregator. Consumers bind this module to receive the
standard tools and the sandboxed JavaScript tool transitively.

The aggregate contains no tool implementation of its own. Application services are still
provided through ports and composed by `:application`.

Use this module from `:application` to receive all supported model tools. It is deliberately a
dependency-only Java library, so adding a new standard tool module requires adding it here.

## Commands

Run from the repository root:

```bash
./gradlew :tools:build
```
