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
3. The immutable conversation UI snapshot does not yet contain those new messages.
4. The user clicks the scroll-to-bottom button.
5. `ConversationHistoryGateway` reads an immutable latest page without mutating AgentManager history.
6. `ConversationUiState` starts a new history generation and merges the latest page into its snapshot only if the page belongs to that generation.
7. The merge refreshes recent rows and appends new rows, while retaining already loaded older rows.
8. The list requests timeline index 0 with a priority that cancels any active wheel or drag scroll, waits for the next layout frame, and scrolls to the newest end.
9. In inline-composer mode index 0 is the composer below the newest message; in fixed-composer mode it is the newest message.
10. An older page that was already in flight is ignored when its history generation no longer matches.

## Result

The user always lands on the newest persisted message when clicking the button,
regardless of how many messages were written in the background since the UI last
refreshed. Already loaded older messages remain available immediately after the jump.
A pending older-history read from the prior UI generation cannot change the snapshot.
Wheel input that was already active before the click cannot move the completed jump away from the latest end.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage`
- `de.heckenmann.visualagent.agent.conversation.AgentConversationHistoryOps.readLatestHistoryPage`
- `de.heckenmann.visualagent.agent.conversation.AgentConversationHistoryOps.readOlderHistoryPage`
- `de.heckenmann.visualagent.ui.conversation.ConversationUiState`
- `de.heckenmann.visualagent.ui.conversation.ConversationHistoryGateway`
- `de.heckenmann.visualagent.ui.conversation.ConversationHistoryPagingEffect`
- `de.heckenmann.visualagent.ui.conversation.ConversationScrollToLatestArea`
- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`

## Acceptance Criteria

- Latest and older history reads return immutable pages without mutating the active AgentManager history.
- The scroll-to-bottom button applies a latest page through a new UI history generation before scrolling.
- Applying a latest page retains every older message already loaded into the UI snapshot.
- The list remains at the newest message when an older-history request was already in progress.
- A stale page from an older history generation cannot change the UI snapshot.
- Persisted, pending, streaming, waiting, loading, empty, and inline-composer rows have explicit timeline item types and stable keys.
- An active wheel scroll is cancelled before the explicit latest navigation is applied.
- Inline mode scrolls through the newest message to the non-sticky composer at timeline index 0.
- The user can browse older history immediately after a completed jump from either partial or fully loaded oldest history.
- The behavior is covered by `ConversationUiStateTest`, `ConversationScrollCoordinatorTest`, `ConversationScrollToLatestInteractionTest`, and `ConversationPanelScrollRegressionTest`.
- The implementation stays within the 300 LOC per-file limit.
