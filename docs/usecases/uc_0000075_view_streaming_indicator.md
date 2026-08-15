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
3. Before the first response chunk arrives, the conversation timeline shows an inline animated "Thinking…" row.
4. As content chunks arrive, the temporary assistant row updates incrementally in the message list.
5. When the stream completes, the temporary row is replaced by the final persisted assistant message.
6. If the stream fails, the timeline is refreshed from persisted history and shows the safe error or retry state.

## Result

Users always know that a response is in progress, and the streaming state does not leak into persisted history.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`
- `de.heckenmann.visualagent.ui.conversation.ComposeConversationMessageList`
- `de.heckenmann.visualagent.protocol.ConversationPort`

## Acceptance Criteria

- An inline thinking row is shown until the first assistant chunk arrives.
- The thinking row shows three dots that pulse with a staggered animation.
- Streaming text is rendered in a temporary assistant row in the message list.
- The thinking and temporary rows are removed after success, failure, or retry.
- Streaming state is not persisted in the database.
- The timeline keeps the newest streaming state visible without persisting it.
