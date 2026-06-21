# UC-0000044: Use Unavailable External Tools

## Goal

Return explicit unavailable responses for browser and web search tools until their backends are implemented.

## Primary Actor

Enabled agent.

## Preconditions

- Browser or search tool is enabled.
- No concrete backend is integrated.

## Main Flow

1. The model calls browser or search.
2. The placeholder tool receives the request.
3. The tool returns a structured unavailable response.

## Result

The model receives a clear failure instead of assuming the action succeeded.

## Tool Calls

- `browser`: returns a structured unavailable result.
- `search`: returns a structured unavailable result.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.BrowserTool`
- `de.heckenmann.visualagent.agent.tools.SearchTool`

## Acceptance Criteria

- Placeholder tools do not perform hidden network/browser work.
- Failure text is explicit enough for the model to choose another strategy.
