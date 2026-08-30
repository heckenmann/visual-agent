# JavaScript Tool Module

`:tool-javascript` contains the GraalJS-backed `javascript:execute` tool and its hardened
workspace/tool bridge. It depends on `:tool-standard` for the provider-neutral tool registry
and is exported transitively by the `:tools` aggregate.

The application supplies the `JavaScriptWorkspaceWriter` implementation. The module itself has
no application, UI, database, or direct host-filesystem dependency.

## Responsibilities

- Executes `javascript:execute` inside GraalJS with timeout and output budgets.
- Exposes hardened workspace reads, writes, deletes, and tool calls through injected ports.
- Maps JavaScript values and failures to the common tool-result representation.

## Commands

Run from the repository root:

```bash
./gradlew :tool-javascript:build
./gradlew :tool-javascript:test
```
