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
5. The scroll coordinator stops an active wheel or drag scroll, enters `JUMPING_TO_LATEST`, and assigns the request a navigation generation.
6. `ConversationHistoryGateway` reads an immutable latest page without mutating AgentManager history.
7. `ConversationUiState` starts a new history generation and atomically replaces its snapshot only if the page belongs to that generation.
8. Starting the latest generation disarms older paging until the published viewport has left the oldest end.
9. If the navigation generation is still current, the coordinator waits for the replacement layout and scrolls once to timeline index 0.
10. In inline-composer mode index 0 is the composer below the newest message; in fixed-composer mode it is the newest message.
11. If the user deliberately moves the list after the jump starts, that input invalidates the navigation generation and takes priority.
12. An older page that was already in flight is ignored when its history generation no longer matches.

## Result

The user always lands on the newest persisted message when clicking the button,
regardless of how many messages were written in the background since the UI last
refreshed. Older messages are removed from the UI snapshot and will be re-fetched again if
the user later scrolls to the top and triggers the older-history loader. A pending or
newly reset older-history load cannot move the list away from the newest message. A deliberate
user scroll that starts after the button action takes priority over the pending automatic jump.
Wheel input that was already active before the click cannot move the completed jump away from the latest end.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage`
- `de.heckenmann.visualagent.agent.conversation.AgentConversationHistoryOps.readLatestHistoryPage`
- `de.heckenmann.visualagent.agent.conversation.AgentConversationHistoryOps.readOlderHistoryPage`
- `de.heckenmann.visualagent.ui.compose.ConversationUiState`
- `de.heckenmann.visualagent.ui.compose.ConversationHistoryGateway`
- `de.heckenmann.visualagent.ui.compose.ConversationHistoryPagingEffect`
- `de.heckenmann.visualagent.ui.compose.ConversationScrollCoordinator`
- `de.heckenmann.visualagent.ui.compose.ConversationScrollToLatestArea`
- `de.heckenmann.visualagent.ui.compose.ConversationPanel`

## Acceptance Criteria

- Latest and older history reads return immutable pages without mutating the active AgentManager history.
- The scroll-to-bottom button applies a latest page through a new UI history generation before scrolling.
- Starting a latest generation does not immediately start another older-history load.
- The list remains at the newest message when an older-history request was already in progress.
- A stale page from an older history generation cannot change the UI snapshot.
- Persisted, pending, streaming, waiting, loading, empty, and inline-composer rows have explicit timeline item types and stable keys.
- User input invalidates a pending scroll-to-latest generation and is not overwritten after the refresh completes.
- An active wheel scroll is stopped before the button starts its navigation generation.
- Inline mode scrolls through the newest message to the non-sticky composer at timeline index 0.
- The user can browse older history immediately after a completed jump from either partial or fully loaded oldest history.
- The behavior is covered by `ConversationUiStateTest`, `ConversationScrollCoordinatorTest`, `ConversationScrollToLatestInteractionTest`, `ConversationHistoryScrollbarPositionTest`, and `ConversationPanelScrollRegressionTest`.
- The implementation stays within the 300 LOC per-file limit.
