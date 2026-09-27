# UC-0000135: Inspect Current Server Process Metrics

## Goal

Let an enabled agent inspect safe resource metrics for the current Visual Agent server process.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `system:process` tool is enabled for the requesting agent.
- The Visual Agent server is running in a JVM that exposes standard process and management APIs.

## Main Flow

1. The agent requests a process snapshot without supplying a PID or operating-system command.
2. The server reads only its own `ProcessHandle`, JVM runtime, and heap metrics.
3. Optional process start time, CPU time, and CPU load are omitted when unavailable.
4. The tool returns the current process ID and bounded numeric JVM/process metrics.

## Result

This tool describes only the Visual Agent server process. It does not enumerate or inspect other
processes and does not expose command lines, environment values, usernames, JVM arguments, or local
paths. Heap values describe the server JVM heap, not total resident-set memory.

## Tool Calls

- `system:process`: request the current process snapshot with `{}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.SystemProcessTool`
- `de.heckenmann.visualagent.agent.tools.JvmServerProcessMetricsProbe`
- `de.heckenmann.visualagent.agent.tools.ServerProcessMetricsProbe`

## Acceptance Criteria

- Uses JDK process and management APIs without invoking a native shell or adding a dependency.
- Inspects only the current server process and explicitly identifies the JVM scope.
- Correctly omits optional metrics when unavailable.
- Tests use deterministic fakes and do not expose other process or environment data.
