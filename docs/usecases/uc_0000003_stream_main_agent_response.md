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
3. While waiting for the first visible assistant chunk, the chat panel displays a `Thinking` indicator beside the newest pending conversation content.
4. Response chunks are emitted as they arrive and replace the waiting indicator with a temporary assistant message.
5. While the user's scroll position is at the bottom of the message list, each new chunk scrolls the conversation to the newest content.
6. If the user has scrolled up to read older messages, new chunks do not disturb the current view; instead, a scroll-to-bottom button appears.
7. After the final chunk, the completed assistant turn is persisted and the view returns to the bottom.

## Result

The user sees progress during longer responses, stays at the bottom by default, and can temporarily read older messages without losing the current scroll position.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.LLMProvider.stream`
- `de.heckenmann.visualagent.agent.AgentManager.streamMessage`
- `de.heckenmann.visualagent.agent.text.AgentResponseCoordinator`
- `de.heckenmann.visualagent.ui.compose.ConversationPanel`

## Acceptance Criteria

- Partial chunks are visible before completion.
- A visible waiting indicator is shown before the first assistant chunk, including for the first request in an empty conversation.
- The persisted conversation contains the final complete assistant response, not partial duplicates.
- The Compose chat panel displays a temporary assistant turn while chunks arrive, then reloads the persisted final history.
- The chat panel auto-scrolls to the bottom whenever a new message appears while the scrollbar is already near the bottom.
- A scroll-to-bottom button appears when the user scrolls up; clicking it reloads the newest messages from the database and animates back to the latest message (see UC-0000093).
