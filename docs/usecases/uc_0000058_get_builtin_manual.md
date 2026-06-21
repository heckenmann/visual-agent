# UC-0000058: Get Built In Manual

## Goal

Let enabled agents inspect built-in documentation for available tools and Markdown formatting.

## Primary Actor

Enabled agent.

## Preconditions

- The manual tool is enabled for the requesting agent.

## Main Flow

1. The model calls the manual tool.
2. The tool lists topics or renders a requested topic.
3. Tool definitions are rendered with IDs, function names, descriptions, and schemas.
4. The result is returned as Markdown text.

## Result

Agents can self-discover available tool usage without global prompt bloat.

## Tool Calls

- `manual` actions: `list`, `show`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.ManualTool`
- `de.heckenmann.visualagent.agent.tools.ToolRegistry`

## Acceptance Criteria

- Unknown topics return available alternatives.
- Tool schemas are included for tool topics.
- Markdown reference is available through `topic=markdown`.
