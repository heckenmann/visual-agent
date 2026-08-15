# Conversation Scroll Regression Analysis

## Symptom

After browsing to older conversation messages, clicking the scroll-to-latest button
could move the viewport to the newest message but leave the user unable to browse
back to the previously loaded history. The panel appeared to stop at the newest
page.

## Root Causes

1. Latest navigation replaced `ConversationUiState.history` with the latest database
   page. Paging is intentionally bounded, so this replacement discarded all older
   messages that the user had already loaded. A later wheel gesture therefore had
   only the latest page available until paging happened again.
2. The oldest-end observer performed the older-page read inline. While that read was
   in flight, it could not observe the newest-to-oldest viewport transition caused by
   a scroll-to-latest action. This made paging depend on request timing.
3. A scroll-to-latest action can race with an active wheel-scroll mutation. The
   explicit navigation must request the newest list position with a priority that
   cancels the existing user-scroll mutation.

## Design

- A latest database page is merged into the UI history by message ID. It refreshes
  recent rows and appends new rows, but never removes already loaded older rows.
- Older-page loads run as child jobs of the viewport observer, so the observer keeps
  receiving state transitions while I/O is pending.
- Explicit latest navigation requests item `0` with `PreventUserInput`, waits for
  the next layout frame, and then scrolls to the reverse-layout newest end.
- Replacing history from an external refresh clears the exhausted-history marker so
  a newly bounded snapshot can page older messages again.

## Regression Coverage

- `ConversationUiStateTest` verifies latest-page merging and history-reset paging.
- `ConversationHistoryPagingRequestTest` covers a delayed older-page request
  across a latest jump.
- `ConversationPanelScrollRegressionTest` uses the production panel and performs
  the sequence: wheel to oldest, click scroll-to-latest, then wheel to oldest again.
