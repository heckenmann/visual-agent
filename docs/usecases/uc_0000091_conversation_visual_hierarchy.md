# UC-0000091: Conversation Visual Hierarchy

## Summary

The conversation panel renders messages with a clear three-tier visual
hierarchy so that the actual chat (user questions and model answers) is
visually dominant, while tool calls, sub-agent results, todo-return
notifications, and thinking blocks recede into the background and are
collapsed by default.

## Actors

- Main agent
- User
- Sub-agent

## Preconditions

- The application is running and the conversation panel is visible.
- At least one user message and one assistant message exist in the history.

## Main Flow

1. The user sends a message to the main agent.
2. New conversation cards fade in and expand gently from their lower edge without
   shifting their content by a distance proportional to the card height. Every dynamic row keeps one
   opaque identity from its pending or streaming state through persistence, so
   completing an answer updates the existing row without flicker or a second
   enter animation. The assistant answer appears as a primary row with a
   transparent panel background, monochrome avatar icon, role label
   "Assistant", and Markdown content. User messages retain their tinted
   background. User and assistant avatars share neutral theme colors and are
   distinguished by their person and agent icons.
3. A tool call fires during the turn. The tool appears as a compact tertiary
   chip showing the tool id and duration, collapsed by default, nested under
   the exact assistant turn that declared it.
4. The user expands the tool chip to inspect input, result, and error blocks.
5. A sub-agent completes a todo and returns a result. The result appears as a
   compact secondary row with a vertical accent bar, collapsed by default.
6. The user expands the sub-agent row to read the full result.
7. If the model emits a thinking block, it appears as a collapsible
   "Thinking" row in `onSurfaceVariant` and `bodySmall` typography.
8. While a request is active, the composer panel indicates activity with a
   soft light band and outline using active theme colors. The
   pinned composer overlays the conversation list with a translucent theme
   surface, so scrolling messages remain visible behind it. Without a pin, it
   is the newest scrollable timeline item. The composer has no
   "Message" label; its placeholder is concise and does not repeat that label.
9. The conversation list remains reverse-laid out so the newest content is at
   the bottom, while its scrollbar position and dragging direction correspond
   to the visible content order. No separate transient "Thinking" status row
   is added to the conversation while a request is active.

## Alternative Flows

- **Tool error:** the chip uses the error color for icon and status text; the
  error detail remains hidden until expanded.
- **Multiple tool rounds:** each assistant turn remains separate, and its
  ordered tool results stay attached to that turn.
- **Empty history:** a centered prompt invites the user to start the
  conversation. It uses a theme-colored agent icon and points to the composer
  without looking like a persisted chat message.

## Tool Calls

- None.

## Code Entry Points

- `ComposeConversationPanel.kt` — hosts the message list and todo-in-progress
  indicator.
- `ComposeConversationMessageList.kt` — dispatches messages to the correct row
  composable and applies visual hierarchy.
- `ComposeConversationEmptyState.kt` — renders the centered first-message
  prompt using the active theme colors.
- `ComposeConversationPanelControls.kt` — renders composer actions with
  accessible hit targets.
- `ComposeMessageRows.kt` — primary `MessageRow` for user and assistant
  messages.
- `ComposeToolMessageRow.kt` — tertiary `ToolMessageRow` chip.
- `ComposeSubAgentMessageRow.kt` — secondary `SubAgentMessageRow` summary.
- `ComposeThinkingRow.kt` — tertiary `ThinkingRow` for reasoning blocks.

## Related Use Cases

- UC-0000002: Send main agent message
- UC-0000003: Stream main agent response
- UC-0000020: Execute tool call
- UC-0000047: Display model thinking blocks
- UC-0000054: Run autonomous processing loop

## Acceptance Criteria

- Assistant rows use a transparent background; user rows use the active theme's
  `secondaryContainer` background and `secondary` accent for a restrained tint.
- No `Card` border or prominent border is visible around either role.
- Tool rows render as inline chips and hide their content by default.
- Sub-agent rows render as compact summaries with a vertical accent bar and
  hide their content by default.
- Thinking rows are collapsed by default and use `bodySmall` /
  `onSurfaceVariant`.
- The composer contains no "Message" label and uses a translucent theme surface.
  Without a pin it scrolls with the conversation. When pinned it overlays the
  list, with enough bottom clearance for the newest message while older content
  scrolls visibly behind it.
- The activity outline is drawn behind and inset from the input content, and
  displays a soft animated theme light band only while a request is active.
- The conversation scrollbar maps offset and drag direction correctly for the
  reverse-layout list; a transient request does not create a conversation row.
- A hovered message displays its timestamp in the message composition without
  opening a separate popup window that could outlive the lazily rendered row.
- The empty-history prompt is centered in the visible area above either composer
  placement, including in a short panel, and uses active theme tokens for its
  icon surface, icon contrast, and supporting text.
- Clear, pin, send, and cancel composer actions provide at least 40 dp hit
  targets; their colors come from the active theme rather than a fixed palette.
- New cards fade in while their height expands from the lower edge; their
  contents do not slide independently. Streaming updates and their
  persisted completion retain the same row identity without a second enter
  animation or visible replacement.
- All conversation-row colors come from `MaterialTheme.colorScheme` tokens; no
  hardcoded colors or alpha magic numbers are used.
