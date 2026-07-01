# UC-0000049: Copy Or Retry Chat Message

## Goal

Let the user copy chat message content and retry assistant rows from the conversation UI.

## Primary Actor

Desktop user.

## Preconditions

- A chat message row is visible.
- The conversation panel is visible.

## Main Flow

1. The user hovers or focuses a message row.
2. The row exposes an icon-only copy action.
3. Assistant rows also expose an icon-only retry action when no request is currently running.
4. Copy places the exact message text on the clipboard.
5. Retry sends the previous user message again through the conversation panel.

## Result

Conversation content can be reused quickly and assistant responses can be retried from context.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeMarkdown`

## Acceptance Criteria

- Copy uses the exact message content.
- Retry is shown only for assistant rows and is disabled while a request is running.
- Retry sends the nearest previous user message.
- Icon buttons remain accessible through tooltips.
