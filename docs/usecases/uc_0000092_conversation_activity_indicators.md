# UC-0000092: Conversation Activity Indicators

## Summary

While work is happening — a todo being processed, a sub-agent running, a tool
call executing, or the main agent streaming a response — the conversation panel
shows ephemeral, inline indicators derived from the existing `InFlightState`
holder. The user can see at a glance where activity is happening without
switching to other panels.

## Actors

- Main agent
- Sub-agent
- Tool execution layer

## Preconditions

- The conversation panel is visible.
- `InFlightStateHolder` is wired to the `ToolEventBus` and the agent status
  callback adapter.

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
6. The sub-agent calls a tool. The `ToolEventBus` emits a `STARTED` event,
   adding the tool id to `InFlightState.pendingToolIds`.
7. The corresponding `ToolMessageRow` chip shows a rotating spinner and the
   text "running…" instead of the duration.
8. The tool finishes. The `FINISHED` event removes the tool id from
  `pendingToolIds`; the chip settles to the final duration.
9. The sub-agent completes. The agent id is removed from `runningAgentIds`,
   the "is working…" chip disappears, and the result row remains.
10. The todo transitions to `COMPLETED` or `CANCELLED`. The in-progress
    indicator disappears and the result row takes its place.
11. The main agent streams a response. The last assistant row shows a subtle,
    animated left-edge accent bar in the primary color.
12. When all activity ends, `totalActive == 0` and all ephemeral indicators are
    gone; the conversation reads as a static transcript.

## Alternative Flows

- **No matching row for a running tool:** the global header indicator still
  reflects the pending tool; only rows that exist get the inline spinner.
- **Todo cancelled:** the in-progress row disappears immediately.

## Tool Calls

- None.

## Code Entry Points

- `ActivityIndicator.kt` — `InFlightState` and `InFlightStateHolder`,
  including `setCurrentTodoInProgress`.
- `AgentStatusCallbackEffect.kt` — wires agent status and todo events into
  `InFlightState`.
- `ComposeConversationPanel.kt` — renders the todo-in-progress indicator above
  the message list.
- `ComposeConversationMessageList.kt` — passes `InFlightState` into tool and
  sub-agent rows.
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
  disappears when it completes or is cancelled.
- A "sub-agent running" chip appears while an agent is `BUSY` and disappears
  when it becomes `IDLE`.
- A tool-call chip shows a spinner and "running…" while the tool is executing
  and the final duration when it finishes.
- The actively streamed assistant row has an animated left-edge accent bar.
- All indicators use only Material3 theme tokens and Compose-native animations.
- No new global state holder is introduced; all indicators derive from
  `InFlightStateHolder` and the existing event buses.
