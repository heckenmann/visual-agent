# UC-0000004: Render Markdown Conversation

## Goal

Render conversation messages and model thinking blocks as Markdown without altering the model-provided text before parsing. The renderer supports GFM (GitHub Flavored Markdown) including tables, strikethrough, block quotes, thematic breaks, italic, nested lists, and server-resolved images.

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
   - Image nodes request validated media through the server-owned conversation port; the UI never performs a direct network or server-filesystem request.
   - Managed workspace images (`workspace:`), server-managed files (`server-file:`), HTTP(S) images, and bounded model-generated image data URLs render with preserved aspect ratio.
   - Explicit client-local files (`client-file:`) are the only images loaded by the desktop process, through its client-image port; these sources are never sent to the server. Unprefixed paths remain server-managed for deterministic routing.
   - Validated canvas snapshots persisted with conversation metadata render as image attachments.
   - Loading, unsupported, missing, and failed images render an accessible local fallback while the surrounding message remains visible.
4. The message row is inserted into the chat list.

Thinking blocks use the same Markdown renderer inside the collapsible thinking row, so formatting remains consistent between model reasoning and the final answer.

## Result

Markdown formatting is displayed in the conversation while preserving the original message content. All standard GFM block and inline types are rendered correctly. Images are re-resolved from persisted message content when history is loaded again. Incomplete streaming text is kept as text until the message is complete, so incomplete image URLs are never requested.

## Model Image Embedding Guidance

The main-agent system prompt tells the model to emit a complete image node when an image should be visible in the conversation:

```markdown
![descriptive alt text](workspace:relative/path/image.png)
```

The model must use only image sources supplied by the user or returned by a tool. `workspace:` and `server-file:` identify server-managed files, direct HTTP(S) URLs identify remote image bytes, and `client-file:` is reserved for an exact client-local path supplied by the user. A validated `data:image/...;base64,...` source may be embedded only when a tool returned it completely. Canvas captures are persisted as conversation attachments automatically. The model must not invent paths or base64 data and must not claim image generation when no image-generation tool returned a usable source.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.application.VisualAgentComposeApp`
- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`
- `de.heckenmann.visualagent.ui.components.ComposeMarkdown`
- `de.heckenmann.visualagent.server.ConversationMediaResolver`
- `de.heckenmann.visualagent.protocol.ConversationPort.resolveImage`
- `de.heckenmann.visualagent.protocol.ClientImagePort`
- `com.mikepenz.markdown.m3.Markdown`

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
- Standard Markdown image syntax renders inline after server-side validation; `client-file:` is the only client-local exception.
- The original Markdown source remains the canonical persisted message content, including thinking markup.
- Markdown formatting inside a thinking block is rendered both in its collapsed latest-line preview and when the block is expanded.
- Unsafe schemes, unregistered workspace paths, redirects, unsupported media, and oversized payloads are rejected without hiding the message.
- Payload MIME is detected from the bytes with Apache Tika and must match the declared image type.
- Images are requested only after a complete Markdown image node is available.
- Canvas image attachments are decoded only after validation by the server boundary.
- Server and client file sources use explicit prefixes so a separated UI cannot mistake a server path for a local path.
