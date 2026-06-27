# UC-0000049: Copy Or Retry Chat Message

## Goal

Let the user copy chat message content and retry assistant rows from the conversation UI.

## Primary Actor

Desktop user.

## Preconditions

- A chat message row is visible.
- The row renderer creates action buttons for the row type.

## Main Flow

1. The user hovers or focuses a message row.
2. The row exposes copy and, where applicable, retry controls.
3. Copy places message text on the clipboard.
4. Retry invokes the configured retry callback for the row.

## Result

Conversation content can be reused quickly and assistant responses can be retried from context.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- Copy uses the exact message content.
- Retry is not shown for unsupported row types.
- Icon buttons remain accessible through tooltips.
