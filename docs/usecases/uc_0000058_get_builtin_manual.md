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
3. The manual lists and renders only tools enabled for the requesting agent.
4. Tool definitions are rendered with provider function names, descriptions, and schemas.
5. The result is returned as Markdown text.

## Result

Agents can self-discover available tool usage without global prompt bloat.

## Tool Calls

- `manual` actions: `list`, `show`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.ManualTool`
- `de.heckenmann.visualagent.agent.tools.ToolRegistry`

## Acceptance Criteria

- Unknown topics return available alternatives.
- Tool schemas are included only for tool topics available to the requester.
- Markdown reference is available through `topic=markdown`.
