# UC-0000073: Edit Chat Message

## Goal

Let the user edit a sent user message in the conversation panel.

## Primary Actor

Desktop user.

## Preconditions

- A user message row is visible in the conversation panel.
- No request is currently streaming.

## Main Flow

1. The user hovers or focuses a user message row.
2. The row exposes an icon-only edit action.
3. The user clicks the edit icon.
4. A modal opens with the current message content in an editable text field.
5. The user changes the content and saves.
6. The message content is updated in the conversation history and persisted.
7. The modal closes and the conversation panel reflects the updated text.

## Result

Users can correct typos or refine earlier messages without re-typing.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.EditMessageModal`

## Acceptance Criteria

- Edit icon is visible only on user message rows.
- Edit is disabled while a request is streaming.
- The modal contains a pre-filled, editable text field.
- Saving updates the persisted message content.
- Cancel dismisses the modal without changes.