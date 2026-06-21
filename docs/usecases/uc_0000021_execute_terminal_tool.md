# UC-0000021: Execute Terminal Tool

## Goal

Allow an enabled agent to run terminal commands from a controlled workspace context.

## Primary Actor

Enabled sub-agent or main orchestration policy where allowed.

## Preconditions

- The terminal tool is enabled for the requesting agent.
- The command is provided as JSON input.

## Main Flow

1. The model calls the terminal tool.
2. The tool parses command input and optional timeout.
3. The command runs in the configured workspace context.
4. Output, exit status, or timeout information is returned.

## Result

Agents can inspect or operate on the local workspace when explicitly permitted.

## Tool Calls

- `terminal`: execute an allowed command with optional timeout handling.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.TerminalTool`
- `de.heckenmann.visualagent.agent.tools.ToolSupport`

## Acceptance Criteria

- The command result is bounded and structured.
- The tool is unavailable when disabled for the agent.
