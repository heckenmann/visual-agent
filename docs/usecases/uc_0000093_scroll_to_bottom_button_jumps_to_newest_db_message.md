# UC-0000093: Scroll-to-Bottom Button Jumps to Newest DB Message

## Goal

Ensure that clicking the floating scroll-to-bottom button always lands on the
newest persisted message in the database, even when background processes
(sub-agents, autonomous tasks) wrote new messages after the UI last refreshed
its in-memory history.

## Primary Actor

Desktop user.

## Preconditions

- The conversation panel is visible.
- The scrollbar is not at the bottom (the button is therefore visible).
- The agent manager has a working conversation store backed by the database.

## Main Flow

1. The user scrolls up to read older messages, which makes the scroll-to-bottom button appear.
2. While the user is reading, a background process persists one or more new messages to the database.
3. The in-memory `conversationHistory` list does not yet contain those new messages.
4. The user clicks the scroll-to-bottom button.
5. The panel clears its in-memory `history` state, then calls `AgentManager.refreshHistoryToLatest()`,
   which wipes the agent's in-memory history and reloads the latest page (up to the page limit)
   directly from the database.
6. The panel refreshes its local `history` state from the agent manager.
7. The panel scrolls the `LazyColumn` to the newest loaded message.

## Result

The user always lands on the newest persisted message when clicking the button,
regardless of how many messages were written in the background since the UI last
refreshed. Older messages are unloaded from memory and will be re-fetched again if
the user scrolls to the top and triggers the older-history loader.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.AgentManager.loadLatestHistory`
- `de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps.loadLatestHistory`
- `de.heckenmann.visualagent.agent.conversation.AgentConversationHistoryOps.loadLatestHistory`
- `de.heckenmann.visualagent.ui.compose.ConversationScrollToBottomArea`
- `de.heckenmann.visualagent.ui.compose.ConversationPanel`

## Acceptance Criteria

- `AgentManager.refreshHistoryToLatest()` clears the in-memory history and reloads the latest page from the database.
- The scroll-to-bottom button calls `refreshHistoryToLatest` before scrolling.
- The behavior is covered by `AgentManagerRefreshHistoryToLatestTest` and by a UI test for `ConversationScrollToBottomArea` that asserts scrolling reaches the last item.
- The implementation stays within the 300 LOC per-file limit.