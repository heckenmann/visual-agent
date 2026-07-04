# UC-0000074: Delete Chat Message

## Goal

Let the user delete a single chat message from the conversation history.

## Primary Actor

Desktop user.

## Preconditions

- A message row is visible in the conversation panel.
- The message is not a system message.

## Main Flow

1. The user hovers or focuses a message row.
2. The row exposes an icon-only delete action (except for system messages).
3. The user clicks the delete icon.
4. The row fades out while the deletion is pending.
5. Once the fade-out finishes, the message is removed from the conversation history and from persistent storage.
6. The conversation panel updates immediately.

## Result

Users can remove accidental or unwanted messages from the conversation.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.agent.AgentManager.deleteMessageById`

## Acceptance Criteria

- Delete icon is visible on user and assistant rows.
- Delete icon is hidden on system messages.
- The message fades out before it is removed.
- The message is removed from in-memory history and persisted storage.
- UI updates without requiring a reload.