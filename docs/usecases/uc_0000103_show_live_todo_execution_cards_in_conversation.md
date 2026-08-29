# UC-0000103: Show live todo execution cards in the conversation

## Goal

Keep the user aware of autonomous todo work by showing each todo as a compact card in the conversation timeline. The card reflects the persisted todo state and receives the same live response stream as the Todo panel without creating a synthetic conversation message or triggering another model request.

## Preconditions

- The desktop application is connected to the application server.
- At least one todo can be created or executed.
- The Conversation panel is visible.

## Main Flow

1. A user action, todo tool call, or orchestration operation creates a todo through the canonical todo service.
2. The server publishes the todo change through `TodoPort`; it does not append a synthetic user/system turn for the visual card.
3. The Conversation panel inserts exactly one todo card using the persisted global activity sequence; this puts a todo created by a user request after that request even when both mutations share a millisecond timestamp.
4. The card displays the description, status, and assigned agent when available.
5. While the todo is `IN_PROGRESS`, an animated working indicator remains visible next to the current output.
6. When execution starts, progress events update the same card with a bounded tail of the latest response lines.
7. The Todo panel consumes the same response presentation behavior and can open the same full-response overlay.
8. When execution completes, fails, is cancelled, or is paused, the card keeps the latest available response tail and updates its status.
9. If the todo is deleted, the server atomically archives its snapshot before removing the active row; the conversation keeps a compact unavailable card based on that persisted snapshot.
10. If an existing todo is genuinely updated, its persisted activity sequence changes and the same card moves down to the current position while retaining its stable id and history.
11. Reloading the conversation reconstructs cards from persisted todos and history at the same chronological positions.

## Alternative Flows

- If no response has arrived yet, an in-progress card shows a compact working indicator.
- If a retry starts with a new execution identifier, stale output is replaced by the current execution output.
- If the full response is clicked, an internal scrollable overlay shows the complete Markdown response and continues observing live updates.
- If the server is unavailable, the existing application error handling remains responsible for reporting the failure.

## Tool Calls

- User-created todos use the existing Todo-panel action and have no additional tool call for card rendering.
- Model-created todos use the existing `todos` tool; rendering the card does not invoke another tool or model turn.

## Code Entry Points

- `de.heckenmann.visualagent.protocol.TodoPort`
- `de.heckenmann.visualagent.protocol.ConversationPort`
- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`
- `de.heckenmann.visualagent.ui.conversation.ConversationTodoCard`
- `de.heckenmann.visualagent.ui.todo.TodoResponseSingleLine`
- `de.heckenmann.visualagent.ui.todo.TodoResponseOverlay`

## Invariants

- Todo cards are presentation-only timeline items and are excluded from model context.
- A todo card keeps one stable id and moves to its latest activity position when it is genuinely updated.
- Conversation messages and todo activity use one database-generated total order. Legacy rows without a sequence use timestamps and a documented deterministic fallback.
- Existing todos are updated only when the objective and scope remain the same; a different objective gets a new todo so prior history remains meaningful.
- One canonical execution stream is fanned out to Conversation, Todo, and the overlay.
- Compact previews are bounded; the canonical full response is not truncated.
- Deleting a todo never removes unrelated conversation content, and its retained snapshot survives panel reloads and application restarts.

## Related Issues

- #253 — Live todo execution cards in conversation with shared response overlay.
- #162 — Todo progress and open behavior in conversation.
- #178 — UI/application protocol boundary.
