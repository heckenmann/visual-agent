# UC-0000129: Inspect JVM Threads

## Goal

Let an enabled agent diagnose thread counts, JVM thread states, deadlocks, and bounded stack
information for the Visual Agent server process.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `system:threads` tool is enabled for the requesting agent.
- The Visual Agent server JVM exposes the standard thread-management MXBean.

## Main Flow

1. The agent requests `summary`, `deadlocks`, or `dump`; summary is the default.
2. For a dump, it may filter by JVM thread state and select bounded thread and stack-frame limits.
3. The server reads the current process through `ThreadMXBean`, caps scanning and returned details,
   and reports whether the output was truncated.
4. The tool returns thread totals, counts by state, detected deadlock participants when available,
   and only the requested bounded thread details.

## Result

The tool is read-only and describes only platform threads in the server JVM, not the desktop client.
`ThreadMXBean` does not enumerate virtual threads; `threadScope` marks this limit in every result.
It does not expose
thread-local values, monitor contents, environment variables, or arbitrary JVM arguments. A missing
deadlock count means the JVM did not provide that optional metric; it is not treated as a failure of
the complete tool.

## Tool Calls

- `system:threads`: inspect thread state, for example
  `{"action":"dump","state":"BLOCKED","maxThreads":20,"maxFrames":8}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.SystemThreadsTool`
- `de.heckenmann.visualagent.agent.tools.JvmThreadDiagnosticsProbe`
- `de.heckenmann.visualagent.agent.tools.ThreadDiagnosticsProbe`

## Acceptance Criteria

- Uses the JVM `ThreadMXBean`; no external process or extra dependency is required.
- Supports summary, deadlock detection, and state-filtered bounded dumps.
- Thread count and frames per thread are capped and validated.
- Tests use bounded JVM snapshots or fakes and do not use timing-based assertions.
