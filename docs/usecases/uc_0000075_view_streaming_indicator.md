# UC-0000075: View Streaming Indicator During Response

## Goal

Show a clear visual indicator while the assistant is generating a response, without corrupting persisted history.

## Primary Actor

Desktop user.

## Preconditions

- A user message has just been sent.
- The assistant response is streaming.

## Main Flow

1. The user sends a message.
2. The conversation panel immediately shows the user message.
3. While the assistant response streams in, a temporary placeholder row appears with a "Thinking…" label and a dot animation at the bottom of the list.
4. As content chunks arrive, the assistant row updates incrementally.
5. When the stream completes, the placeholder is replaced by the final persisted assistant message.
6. If the stream fails, the placeholder is replaced by the persisted error or retry state.

## Result

Users always know that a response is in progress, and the streaming state does not leak into persisted history.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.StreamingIndicator`
- `de.heckenmann.visualagent.agent.AgentManager.streamMessage`

## Acceptance Criteria

- A placeholder row is shown while the assistant is streaming.
- The placeholder is removed after success, failure, or retry.
- Streaming state is not persisted in the database.
- The indicator is visible at the bottom of the message list.