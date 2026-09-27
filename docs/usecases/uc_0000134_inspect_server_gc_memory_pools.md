# UC-0000134: Inspect Server Garbage Collection and Memory Pools

## Goal

Let an enabled agent inspect cumulative garbage-collection activity and current memory-pool usage
for the Visual Agent server JVM.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `system:gc` tool is enabled for the requesting agent.
- The JVM exposes its standard garbage-collector and memory-pool management beans.

## Main Flow

1. The agent requests a GC snapshot without supplying host paths or JVM-specific commands.
2. The server reads collector totals and current pool usage through the JVM management APIs.
3. The server bounds the number and length of returned names and omits unsupported numeric metrics.
4. The tool returns collector counts/times and memory-pool usage in bytes, with an explicit runtime
   scope and truncation flag.

## Result

This is a read-only snapshot of the Visual Agent server JVM. Collection counts and times are totals
since JVM startup, not per-request measurements. The tool does not trigger GC, create heap dumps, or
expose object contents, arbitrary JVM arguments, environment values, or filesystem paths.

## Tool Calls

- `system:gc`: request a snapshot with `{}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.SystemGcTool`
- `de.heckenmann.visualagent.agent.tools.JvmGcDiagnosticsProbe`
- `de.heckenmann.visualagent.agent.tools.GcDiagnosticsProbe`

## Acceptance Criteria

- Uses standard JVM management APIs without an additional dependency.
- Bounds collector and memory-pool entries and individual names.
- Omits unavailable counts, times, and pool measurements without failing the full result.
- Tests use deterministic fakes and do not assert timing or require a particular JVM collector.
