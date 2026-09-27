# UC-0000140: Inspect Database Health

## Goal

Allow the main agent to diagnose server database reachability and schema migration health without querying or exposing application records.

## Preconditions

- The `diagnostics:database` tool is registered and enabled for the main agent.
- The Visual Agent server database client is configured.

## Main Flow

1. The main agent calls `diagnostics_database` with an empty JSON object.
2. The server executes a constant `SELECT 1` connectivity probe.
3. If reachable, the server reads only the Flyway schema-history version and failed-migration count.
4. The tool reports a normalized `healthy`, `degraded`, or `unavailable` status and omits raw driver errors, SQL, credentials, paths, and application data.

## Security and Limits

- The tool accepts no SQL, table names, or other query parameters.
- It performs no writes and does not inspect application records.
- Failure details are represented by fixed categories; driver messages and credentials are not returned.
- It is exposed to the main agent only.

## Tool Calls

- Provider function `diagnostics_database`: `{}`. Internal configuration ID: `diagnostics:database`.

## Code Entry Points

- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/DiagnosticsDatabaseTool.kt`
- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/api/ServerDatabaseDiagnosticsPort.kt`
- `application/src/main/kotlin/de/heckenmann/visualagent/agent/tools/ServerDatabaseDiagnosticsPortAdapter.kt`

## Acceptance Criteria

- The tool performs only static, read-only database probes.
- Reachability and schema-metadata availability are reported independently.
- The current successful migration version and failed migration count are bounded scalar values.
- No application data, SQL text, credentials, database path, or raw exception message is returned.
- Deterministic tool and H2 integration tests cover healthy, unavailable, and malformed-input behavior.
