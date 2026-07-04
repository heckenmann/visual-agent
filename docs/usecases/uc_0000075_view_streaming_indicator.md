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
3. While the assistant response streams in, a fixed status line with an animated "Thinking…" label and three pulsing dots appears between the message list and the input field.
4. The status line does not add or remove items from the message list, so the list does not jump when the indicator appears or disappears.
5. As content chunks arrive, the assistant row updates incrementally.
6. When the stream completes, the status line is replaced by the final persisted assistant message.
7. If the stream fails, the status line is replaced by the persisted error or retry state.

## Result

Users always know that a response is in progress, and the streaming state does not leak into persisted history.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.StreamingStatusLine`
- `de.heckenmann.visualagent.agent.AgentManager.streamMessage`

## Acceptance Criteria

- A status line is shown while the assistant is streaming.
- The status line shows three dots that pulse with a staggered animation.
- The status line is positioned between the message list and the input field, not inside the list.
- The status line is removed after success, failure, or retry.
- Streaming state is not persisted in the database.
- The indicator does not change the message list height.