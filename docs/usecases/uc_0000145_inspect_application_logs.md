# UC-0000145: Inspect Recent Application Logs

## Goal

Let the main agent inspect a bounded, recent window of Visual Agent server log events without
access to arbitrary log files or filesystem paths.

## Primary Actor

The main agent, after an administrator explicitly enables log diagnostics.

## Preconditions

- `diagnostics:logs` is globally enabled in tool settings.
- The server is using its configured Logback runtime.

## Main Flow

1. The main agent calls `diagnostics_logs` with optional filters for severity, logger name, message
   text, correlation ID, and result count.
2. The server searches a fixed-capacity in-memory ring buffer containing recent log events only.
3. Common credential forms are redacted before an event enters the buffer; messages and fields are
   bounded before results are returned.
4. Matching results are returned newest-first with timestamps, logger, thread, level, message, and
   correlation ID when available.

## Result

The model can correlate recent server events without arbitrary file reads. The buffer is volatile,
bounded, and never writes a copy of logs to disk. Secret redaction is defense-in-depth and does not
make enabling this capability safe for an untrusted agent.

## Tool Calls

- `diagnostics_logs({})`: return the newest 50 matching entries.
- `diagnostics_logs({"level":"ERROR","query":"connection","limit":25})`: filter by level and
  message text.
- `diagnostics_logs({"loggerContains":"provider","correlationId":"request-123"})`: filter by
  logger and correlation ID.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.DiagnosticsLogsTool`
- `de.heckenmann.visualagent.agent.tools.ServerLogDiagnosticsPortAdapter`
- `de.heckenmann.visualagent.agent.tools.ServerLogBuffer`
- `de.heckenmann.visualagent.agent.tools.api.ServerLogDiagnosticsPort`

## Acceptance Criteria

- Results come only from the bounded in-memory appender; caller-provided paths and file reads are
  unsupported.
- Result count and filter lengths are limited, and newest events are returned first.
- API keys, passwords, bearer tokens, URL credentials, and PEM private-key blocks are redacted
  before retention.
- Only the main agent may use the tool, and it is disabled by default until explicitly enabled.
- Tests cover filtering, bounds, redaction, Logback attachment, and permission policy.
