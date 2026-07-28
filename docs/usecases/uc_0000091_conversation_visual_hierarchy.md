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
2. The assistant answer appears as a primary row: borderless background tint,
   avatar icon, role label "Assistant", and Markdown content.
3. A tool call fires during the turn. The tool appears as a compact tertiary
   chip showing the tool id and duration, collapsed by default.
4. The user expands the tool chip to inspect input, result, and error blocks.
5. A sub-agent completes a todo and returns a result. The result appears as a
   compact secondary row with a vertical accent bar, collapsed by default.
6. The user expands the sub-agent row to read the full result.
7. If the model emits a thinking block, it appears as a collapsible
   "Thinking" row in `onSurfaceVariant` and `bodySmall` typography.

## Alternative Flows

- **Tool error:** the chip uses the error color for icon and status text; the
  error detail remains hidden until expanded.
- **Empty history:** a placeholder row invites the user to start the
  conversation.

## Tool Calls

- None.

## Code Entry Points

- `ComposeConversationPanel.kt` — hosts the message list and todo-in-progress
  indicator.
- `ComposeConversationMessageList.kt` — dispatches messages to the correct row
  composable and applies visual hierarchy.
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

- User and assistant rows use a borderless tinted background; no `Card` or
  prominent border is visible.
- Tool rows render as inline chips and hide their content by default.
- Sub-agent rows render as compact summaries with a vertical accent bar and
  hide their content by default.
- Thinking rows are collapsed by default and use `bodySmall` /
  `onSurfaceVariant`.
- All conversation-row colors come from `MaterialTheme.colorScheme` tokens; no
  hardcoded colors or alpha magic numbers are used.
