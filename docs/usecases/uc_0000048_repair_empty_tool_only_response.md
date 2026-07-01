# UC-0000048: Repair Empty Tool Only Response

## Goal

Replace empty or generic assistant responses with a concise summary of the latest tool result.

## Primary Actor

Desktop user.

## Preconditions

- A model request triggered a tool call.
- The assistant response is empty or only asks for context despite available tool output.

## Main Flow

1. Tool execution produces a compact preview.
2. The chat wiring stores the latest preview.
3. The model returns empty or generic clarification text.
4. The chat wiring substitutes a user-facing message that references the executed tool result.

## Result

The user sees useful feedback even when the model fails to summarize tool-only work.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- A non-empty tool preview is required before substitution.
- Normal assistant responses are not rewritten.
- The generated message is concise and user-facing.
