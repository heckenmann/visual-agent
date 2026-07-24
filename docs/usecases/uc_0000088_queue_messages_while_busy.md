# UC-0000088: Queue Messages While Busy

## Goal

Allow the user to send messages to the main agent while a request is already in flight. Messages land in a visible queue and are delivered when the agent becomes idle.

## Primary Actor

Desktop user.

## Preconditions

- The main agent is busy (streaming a response or processing tool calls).
- The conversation panel is visible.

## Main Flow

1. The user types a message and presses Enter or clicks Send while the main agent is busy.
2. The message is enqueued in an in-memory queue instead of being dropped.
3. A queue strip appears above the text input field showing queued messages.
4. When the main agent becomes idle and no tool calls are in flight, the queue is flushed automatically.
5. Each queued message is sent as a separate request (one-by-one mode) or combined into one request (all-at-once mode).
6. The user can click "Send now" on any queued message to interrupt the current request and send that message immediately.
7. The user can clear the queue without sending any messages.

## Result

Messages are never lost when the main agent is busy. The user can prioritize messages via the interrupt button.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.MessageQueue`
- `de.heckenmann.visualagent.ui.compose.MessageQueueStrip`
- `de.heckenmann.visualagent.ui.compose.executeSend`

## Acceptance Criteria

- Messages sent while `sending == true` are enqueued, not dropped.
- The queue strip is visible when the queue is non-empty.
- The queue strip shows source icon, truncated content, and a "Send now" button per message.
- Clicking "Send now" cancels the current request and sends the selected message.
- The queue is flushed automatically when the agent becomes idle.
- A "clear queue" button removes all queued messages.
- The queue is in-memory and transient (not persisted).
