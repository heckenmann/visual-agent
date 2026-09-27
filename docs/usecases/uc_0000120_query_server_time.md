# UC-0000120: Query Visual Agent Server Time

## Goal

Allow an enabled model to query the clock and timezone of the Visual Agent server, including when
the UI is connected to a remote server.

## Primary Actor

Main agent or an enabled sub-agent.

## Preconditions

- The `system:time` tool is enabled for the requesting agent.

## Main Flow

1. The model calls the time tool, optionally supplying a valid Java `zoneId`.
2. The server reads one instant from its injected `Clock`.
3. The tool returns UTC, server-local time/zone/offset, epoch milliseconds, and the requested-zone
   representation when supplied.

## Result

The model receives deterministic, structured server-clock data without an operating-system command
or assumptions about the desktop client's timezone.

## Tool Calls

- `system:time`: read-only server time; optional input is `{"zoneId":"Europe/Berlin"}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.ServerTimeTool`
- `java.time.Clock`

## Acceptance Criteria

- UTC, server-local time, timezone/offset, and epoch milliseconds describe the same instant.
- Tests use a fixed clock and do not depend on wall-clock timing.
- Invalid timezones return a bounded validation error.
