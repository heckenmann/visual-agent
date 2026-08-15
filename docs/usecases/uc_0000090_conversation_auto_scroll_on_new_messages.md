# UC-0000090: Conversation Auto-Scroll on New Messages

## Goal

Ensure the conversation panel reliably scrolls to the latest message whenever the conversation changes, including during streaming responses where the last assistant message is updated in place.

## Primary Actor

Desktop user.

## Preconditions

- The conversation panel is visible.
- There is at least one message in the conversation history.

## Main Flow

1. The user sends a message or the main agent streams a response.
2. The conversation displays new content:
   - A new message is appended, or
   - The just-submitted user message is displayed as a temporary pending row before persistence completes, or
   - The content of the last assistant message grows during streaming.
3. The conversation timeline detects new content and asks the scroll coordinator to follow the latest item after layout.
4. While the user is following the newest message, each visible assistant update and structural viewport-size change keeps the newest content visible.
5. If the user browses older history, viewport changes preserve that position instead of forcing the list back to the newest message.
6. User input invalidates pending programmatic navigation immediately, but enters history-browsing mode only after it actually moves the list.
7. Follow mode is restored only after Compose publishes an actual return to index 0 with zero offset.
8. A fixed composer reserves space below the timeline and does not change list padding or schedule delayed resize navigation.

## Result

The conversation panel follows the latest message automatically without overriding deliberate history browsing.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`
- `de.heckenmann.visualagent.ui.conversation.ConversationUiState`
- `de.heckenmann.visualagent.ui.conversation.ConversationTimelineItem`
- `de.heckenmann.visualagent.ui.conversation.ConversationScrollCoordinator`
- `de.heckenmann.visualagent.ui.conversation.ConversationScrollOnChangeEffect`
- `de.heckenmann.visualagent.ui.conversation.ConversationScrollEffects`
- `de.heckenmann.visualagent.ui.conversation.ConversationStartupScrollEffect`

## Acceptance Criteria

- On startup and after reopening, the list starts at index 0 with zero offset if history is not empty.
- In inline mode the composer below the newest message is visible at the latest end; in fixed mode the newest message is visible.
- When a new message is appended the list scrolls to the bottom.
- When a user submits a message, the temporary pending row scrolls into view before the request completes.
- When the last message content changes during streaming the list scrolls to the bottom.
- When the structural timeline viewport changes height while the list is at the newest end, the newest message remains visible.
- When the user is browsing older history, composer and panel size changes preserve the browsed position.
- The fixed composer reserves layout space instead of overlaying the timeline or feeding its measured height back into list padding.
- No fixed delay or retry loop is used for viewport resize navigation.
- Pointer input that consumes no scroll distance must not leave the coordinator in history-browsing mode.
- The first actual user movement after a stale latest snapshot must enter history-browsing mode.
- Startup restoration is covered with a newly created manager and variable-height Markdown history.
- The auto-scroll effect is covered by coordinator and full-panel regression tests.
- The implementation stays within the 300 LOC per-file limit.
