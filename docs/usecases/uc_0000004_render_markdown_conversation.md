# UC-0000004: Render Markdown Conversation

## Goal

Render conversation messages as Markdown without altering the model-provided message text before parsing.

## Primary Actor

Desktop user.

## Preconditions

- A conversation message exists.
- The chat message renderer is available.

## Main Flow

1. A message is mapped to the UI model.
2. The renderer passes message content directly to the CommonMark parser.
3. Markdown nodes are converted into Compose Multiplatform UI nodes.
4. The message row is inserted into the chat list.

## Result

Markdown formatting is displayed in the conversation while preserving the original message content.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeMarkdown`

## Acceptance Criteria

- Markdown input is not pre-normalized or heuristically rewritten.
- Code blocks, lists, and regular paragraphs render consistently.
