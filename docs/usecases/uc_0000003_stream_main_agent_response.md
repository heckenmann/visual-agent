# UC-0000003: Stream Main Agent Response

## Goal

Display assistant output incrementally while the provider is still generating the response.

## Primary Actor

Desktop user.

## Preconditions

- The active provider supports streaming.
- The chat panel is connected to the main window wiring.

## Main Flow

1. The user sends a message.
2. The application starts a streaming provider request.
3. Response chunks are emitted as they arrive.
4. The chat panel updates the visible assistant message incrementally.
5. After the final chunk, the completed assistant turn is persisted.

## Result

The user sees progress during longer responses and the final completed message is stored.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.LLMProvider.stream`
- `de.heckenmann.visualagent.agent.AgentManager.streamMessage`
- `de.heckenmann.visualagent.agent.text.AgentResponseCoordinator`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- Partial chunks are visible before completion.
- The persisted conversation contains the final complete assistant response, not partial duplicates.
