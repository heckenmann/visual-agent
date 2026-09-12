# UC-0000112: Show Idle Follow-up Question Suggestions

## Goal

Offer short, optional follow-up questions after the main agent has completed an answer and the user has remained idle. Suggestions are visual inspiration only and never become a user message automatically.

## Primary Actor

Desktop user.

## Preconditions

- A model response was successfully persisted as the newest assistant turn.
- Follow-up suggestions are enabled in Conversation settings.
- The composer is empty and no request or queued message is active. An already focused composer
  remains eligible; focus alone is not considered user input.

## Main Flow

1. The server publishes a completion event containing the persisted assistant entry UUID.
2. The conversation panel waits for the configured idle delay.
3. The server requests a bounded set of follow-up questions from the active provider without tools or persistence.
4. The panel validates and displays each question as translucent ghost text in the empty composer.
5. Each question is typed by grapheme cluster, held briefly, erased, and followed by the next question.
6. The cycle repeats while the same assistant turn remains eligible.
7. Any actual edit, paste, send, queue, cancellation, conversation switch, or newer response removes
   the ghost text and consumes that turn's suggestion opportunity. Focus changes alone do not remove it.

## Alternative Flows

- If the provider is unavailable, times out, is cancelled, or returns malformed, unsafe, duplicated, or oversized output, no suggestion is shown and no disruptive error is displayed.
- If settings disable suggestions, pending work is cancelled immediately.
- Initial history, welcome/fallback messages, and partial or failed responses do not start this flow.

## Result

The user may use the inspiration to compose a message while retaining complete control of the draft. No suggestion is sent, persisted, or used to invoke tools.

## Tool Calls

- None. The suggestion request is a dedicated server/protocol operation and exposes no model tools.

## Code Entry Points

- `de.heckenmann.visualagent.protocol.ConversationSuggestionPort`
- `de.heckenmann.visualagent.server.SpringConversationSuggestionPort`
- `de.heckenmann.visualagent.ui.conversation.ConversationSuggestionController`
- `de.heckenmann.visualagent.ui.conversation.ConversationInputArea`
- `de.heckenmann.visualagent.ui.settings.conversationSettingsSection`

## Acceptance Criteria

- Suggestions start only after a newly completed persisted assistant response and the configured idle delay.
- The active provider receives one bounded, tool-less request with strict JSON-array output requirements.
- Invalid, duplicate, unsafe, stale, failed, timed-out, or cancelled results render nothing.
- Ghost text never changes the actual input, selection, send state, accessibility semantics, or conversation history.
- Grapheme-aware typing, cursor blinking, hold, erasing, and cycling are deterministic and cancellable.
- Settings are persisted, bounded, editable, and applied without restarting the application.
