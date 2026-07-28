# UC-0000046: Load Older Conversation Messages

## Goal

Let the user page older persisted conversation messages into the visible chat history by scrolling to the top of the chat list.

## Primary Actor

Desktop user.

## Preconditions

- Persisted history contains messages older than the currently loaded page.
- The chat panel is visible.

## Main Flow

1. The user scrolls to the top of the chat list.
2. The conversation panel detects that the first visible item is at index 0 and near the top scroll offset.
3. The panel triggers `AgentManager.loadOlderHistory()` on a background coroutine.
4. The manager loads one older page from persistence using `loadedHistoryCount` as the offset.
5. The panel prepends the older messages without duplicating existing rows.
6. The panel preserves the user's scroll position by adjusting the `LazyListState` to the previously-topmost item.
7. A loading indicator is shown at the top of the list while the fetch is in progress.
8. When no older messages remain, the panel stops re-triggering the load.

## Alternative Flows

- **Manual trigger:** The user can still manually trigger a load via the History button if present.
- **Empty result:** When `loadOlderHistory()` returns no new messages, `hasMoreHistory` is set to `false` and no further automatic loads are attempted until the conversation is reset.

## Result

Long conversations can be reviewed incrementally without loading all history on startup. Scrolling to the top automatically loads the next page.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.ConversationOlderHistoryLoader`
- `de.heckenmann.visualagent.ui.compose.OlderHistoryLoadingIndicator`
- `de.heckenmann.visualagent.agent.AgentManager.loadOlderHistory`
- `de.heckenmann.visualagent.knowledge.ConversationStore.getConversationMessagesPage`

## Acceptance Criteria

- Scrolling to the top of the chat list automatically loads one older page of conversation messages.
- After loading older messages, the user's scroll position is preserved (the previously-topmost message remains visible).
- A loading indicator is shown while older messages are being fetched.
- When no older messages remain, no further automatic loads are triggered.
- Already visible messages are not duplicated.
- Startup history remains bounded.
- History paging is deterministic.
