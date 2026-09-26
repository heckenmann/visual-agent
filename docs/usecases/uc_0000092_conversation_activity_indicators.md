# UC-0000092: Conversation Activity Indicators

## Summary

While work is happening — a todo being processed, a sub-agent running, a tool
call executing, or the main agent streaming a response — the conversation panel
shows ephemeral row indicators and a composer activity outline derived from the
existing `InFlightState` holder. The user can see where activity is happening
without switching to other panels.

## Actors

- Main agent
- Sub-agent
- Tool execution layer

## Preconditions

- The conversation panel is visible.
- `InFlightStateHolder` is wired to the protocol-owned `ActivityPort` and the
  agent status callback adapter.

## Main Flow

1. The autonomous coordinator assigns a pending todo to a sub-agent and moves
   it to `IN_PROGRESS`.
2. The todo event bus updates `InFlightState.currentTodoInProgress`.
3. A live "Todo '...' in progress" indicator appears in the conversation list,
   pulsing in the primary theme color.
4. The sub-agent starts its job. `InFlightState.runningAgentIds` now contains
   the agent id.
5. If a matching sub-agent result row is already visible, an animated
   "Agent '...' is working…" chip appears inside it.
6. The sub-agent calls a tool. `ActivityPort` emits a `STARTED` event,
   adding the tool id to `InFlightState.pendingToolIds`.
7. The corresponding `ToolMessageRow` chip shows a rotating spinner and the
   text "running…" instead of the duration.
8. The tool finishes. The `FINISHED` event removes the tool id from
  `pendingToolIds`; the chip settles to the final duration.
9. The sub-agent completes. The agent id is removed from `runningAgentIds`,
   the "is working…" chip disappears, and the result row remains.
10. The todo transitions to `COMPLETED` or `CANCELLED`. The card remains in the
    conversation timeline with its latest response tail and terminal status;
    only the animated working indicator disappears.
11. The main agent streams a response. The last assistant row shows a subtle,
    animated left-edge accent bar in the primary color and renders received
    Markdown incrementally. Completed blocks remain stable while an unfinished
    Markdown tail continues to update as new chunks arrive.
12. Before the first streamed token arrives, a soft theme-colored light band
    moves across the composer's translucent background and outline while
    `InFlightState.totalActive > 0`. It fades in and out with request activity.
    No separate pre-stream "Thinking" row is added to the conversation.
13. When the composer is pinned at the panel bottom, the message list has enough
    bottom clearance to keep its newest content readable above the translucent
    overlay. Older messages can scroll behind it. Without a pin, the composer is
    the newest scrollable item.
14. When all activity ends, `totalActive == 0` and all ephemeral indicators are
    gone; the conversation reads as a static transcript.

## Alternative Flows

- **No matching row for a running tool:** the global header indicator still
  reflects the pending tool; only rows that exist get the inline spinner.
- **Todo cancelled:** the working indicator disappears, while the todo card
  remains available with its cancellation status and latest response tail.

## Tool Calls

- None.

## Code Entry Points

- `ActivityIndicator.kt` — `InFlightState` and `InFlightStateHolder`,
  including `setCurrentTodoInProgress`.
- `AgentStatusCallbackEffect.kt` — wires agent status and todo events into
  `InFlightState`.
- `ComposeConversationPanel.kt` — derives request activity and places the
  pinned composer over the message viewport.
- `ComposeConversationMessageList.kt` — renders conversation timeline rows
  without a separate pre-stream waiting row.
- `ComposeStreamingMarkdown.kt` — uses the Markdown renderer's append-only
  streaming state for live assistant and todo-response content without
  rewriting the raw Markdown.
- `ComposeToolMessageRow.kt` — renders the in-flight spinner on tool chips.
- `ComposeSubAgentMessageRow.kt` — renders the running chip on sub-agent rows.
- `ComposeConversationIndicators.kt` — shared indicator composables
  (`TodoInProgressRow`, `SubAgentRunningChip`, `ToolInFlightSpinner`,
  `StreamingAccentBar`).

## Related Use Cases

- UC-0000072: Show in-flight activity indicator
- UC-0000080: Cancel running agent actions
- UC-0000054: Run autonomous processing loop
- UC-0000091: Conversation visual hierarchy

## Acceptance Criteria

- A "todo in progress" indicator appears when a todo is `IN_PROGRESS` and
  disappears when it completes or is cancelled; the associated todo card remains
  in the timeline and reflects the terminal state.
- A "sub-agent running" chip appears while an agent is `BUSY` and disappears
  when it becomes `IDLE`.
- A tool-call chip shows a spinner and "running…" while the tool is executing
  and the final duration when it finishes.
- The actively streamed assistant row has an animated left-edge accent bar.
- Streamed assistant Markdown is rendered before the response completes;
  headings, lists, links, inline formatting, and code content appear as their
  syntax becomes complete. Incomplete Markdown remains visible and no raw
  response content is dropped or normalized.
- Replacing a transient streamed row with its persisted assistant message does
  not duplicate or lose Markdown content.
- The pre-stream composer light band and outline derive from
  `InFlightState.totalActive`, preserve the translucent surface, and fade away
  after successful, failed, timed out, or cancelled requests complete.
- With a pinned composer, the newest message remains visible above the input
  overlay while older messages can scroll behind it.
- All indicators use only Material3 theme tokens and Compose-native animations.
- No new global state holder is introduced; all indicators derive from
  `InFlightStateHolder` and the existing event buses.
