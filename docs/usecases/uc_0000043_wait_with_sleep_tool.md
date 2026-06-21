# UC-0000043: Wait With Sleep Tool

## Goal

Allow enabled agents to intentionally wait for a bounded duration before continuing.

## Primary Actor

Enabled agent.

## Preconditions

- The sleep tool is enabled for the requesting agent.
- Requested duration is within tool limits.

## Main Flow

1. The model calls the sleep tool.
2. The tool validates duration input.
3. Execution suspends for the bounded duration.
4. A completion result is returned.

## Result

Agents can wait for asynchronous state changes without busy-looping.

## Tool Calls

- `sleep`: wait for a bounded duration.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.SleepTool`
- `de.heckenmann.visualagent.agent.tools.ToolRegistry`

## Acceptance Criteria

- Sleep duration is bounded.
- Disabled sleep tool is not exposed to the agent.
