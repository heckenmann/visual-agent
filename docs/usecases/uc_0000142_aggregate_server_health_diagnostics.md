# UC-0000142: Aggregate Server Health Diagnostics

## Goal

Let the main agent summarize core Visual Agent server health without returning detailed configuration, credentials, database records, or raw subsystem errors.

## Actors

- Main agent with `diagnostics:health` enabled.
- Visual Agent server database and active provider.

## Preconditions

- The main agent is allowed to use health diagnostics.

## Main Flow

1. The model calls `diagnostics_health` with an empty object.
2. The server checks database connectivity/schema status, provider connectivity/model availability, and effective configuration validity.
3. The server reports one overall status and one status for each component: database, provider, and configuration.
4. For degraded or unavailable components, the server includes stable reason codes without copying raw exception or configuration text.

## Alternative Flows

- An unavailable database makes overall health unavailable.
- A degraded database, unavailable provider, unavailable selected model, or invalid configuration makes overall health degraded.
- If a probe cannot produce a safe status, the tool returns a normalized unavailable result.

## Result

The main agent can provide a concise operational summary and use the individual diagnostic tools for a follow-up without seeing secrets or application data. Reason codes identify conditions such as `database_unreachable`, `provider_timeout`, or `active_model_missing`; they do not expose raw subsystem messages.

Stable reason codes include `database_unreachable`, `migration_history_unavailable`,
`failed_migrations_present`, `provider_not_enabled`, `provider_timeout`,
`provider_unreachable`, `provider_authentication_failed`, `provider_quota_exhausted`,
`provider_model_unavailable`, `provider_model_access_denied`, `provider_request_failed`,
`selected_model_not_discovered`, `selected_model_availability_unknown`, and configuration codes
`active_provider_profile_missing`, `active_provider_profile_disabled`, `active_model_missing`,
`active_model_not_selectable`, `context_window_non_positive`, `timeout_non_positive`, and
`max_parallel_sub_agents_non_positive`. Unknown provider errors always map to
`provider_request_failed`; raw provider errors are never copied into aggregate health output.

## Tool Calls

- `diagnostics_health({})`: reports aggregate health statuses and safe reason codes for the server database, active provider, and configuration.

## Code Entry Points

- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/DiagnosticsHealthTool.kt`
- `application/src/main/kotlin/de/heckenmann/visualagent/agent/tools/ServerHealthDiagnosticsPortAdapter.kt`
- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/api/ServerHealthDiagnosticsPort.kt`
