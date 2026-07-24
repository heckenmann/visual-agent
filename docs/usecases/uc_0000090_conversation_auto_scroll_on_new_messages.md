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
2. The conversation history is updated:
   - A new message is appended, or
   - The content of the last assistant message grows during streaming.
3. The conversation list detects the change and scrolls to the bottom after a short debounce.
4. The user always sees the newest content without manually scrolling.

## Result

The conversation panel stays pinned to the latest message automatically, including during streaming.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.ConversationScrollOnChangeEffect`
- `de.heckenmann.visualagent.ui.compose.ConversationStartupScrollEffect`

## Acceptance Criteria

- On startup the list scrolls to the bottom if history is not empty.
- When a new message is appended the list scrolls to the bottom.
- When the last message content changes during streaming the list scrolls to the bottom.
- The auto-scroll effect is covered by a regression test.
- The implementation stays within the 300 LOC per-file limit.
