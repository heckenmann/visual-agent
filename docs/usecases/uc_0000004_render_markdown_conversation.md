# UC-0000004: Render Markdown Conversation

## Goal

Render conversation messages as Markdown without altering the model-provided message text before parsing. The renderer supports GFM (GitHub Flavored Markdown) including tables, strikethrough, block quotes, thematic breaks, italic, and nested lists.

## Primary Actor

Desktop user.

## Preconditions

- A conversation message exists.
- The chat message renderer is available.

## Main Flow

1. A message is mapped to the UI model.
2. The renderer passes message content directly to the CommonMark parser with Autolink, GFM Tables, and GFM Strikethrough extensions enabled.
3. Markdown nodes are converted into Compose Multiplatform UI nodes:
   - Paragraphs, headings, code blocks, and lists render as before.
   - Block quotes render with a left accent bar and indentation.
   - Thematic breaks render as a horizontal divider line.
   - Tables render with column alignment (left/center/right) and horizontal scrolling for wide tables.
   - Italic text renders with `FontStyle.Italic`.
   - Strikethrough text renders with `TextDecoration.LineThrough`.
   - Nested lists render with per-level left indentation.
4. The message row is inserted into the chat list.

## Result

Markdown formatting is displayed in the conversation while preserving the original message content. All standard GFM block and inline types are rendered correctly.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeMarkdown`
- `de.heckenmann.visualagent.ui.compose.ComposeMarkdownParser`

## Acceptance Criteria

- Markdown input is not pre-normalized or heuristically rewritten.
- Code blocks, lists, tables (GFM pipe syntax), and regular paragraphs render consistently.
- Block quotes render with a visual left border.
- Thematic breaks render as a horizontal divider.
- Italic text renders in italic.
- Strikethrough text renders with a line through it.
- GFM tables render with correct column alignment (left/center/right) from the Markdown source.
- Wide tables scroll horizontally instead of clipping.
- Nested lists are visually indented per nesting level.
- Empty table cells render gracefully without visible empty bordered boxes.