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
4. The card displays the description and assigned agent when available. Its canonical todo status is shown as both readable text and a matching semantic icon (pending, in progress, completed, or cancelled).
5. While the todo is `IN_PROGRESS`, an animated working indicator remains visible next to the current output.
6. When execution starts, progress events update the same card with a bounded tail of the latest response lines.
7. The Todo panel consumes the same response presentation behavior and can open the same full-response overlay.
8. When execution completes, fails, is cancelled, or is paused, the card keeps the latest available response tail and updates its status.
9. If the todo is deleted, the server atomically archives its snapshot before removing the active row; the conversation keeps a compact unavailable card based on that persisted snapshot.
10. If an existing todo is genuinely updated, its persisted activity sequence changes and the same card moves down to the current position while retaining its stable id and history.
11. Reloading the conversation reconstructs cards from persisted todos and history at the same chronological positions.
12. New and updated cards participate in the same always-latest auto-scroll as messages and tool results (UC-0000003), including when the persisted conversation history did not change. This applies with both pinned and inline input placement.

## Alternative Flows

- If no response has arrived yet, an in-progress card shows a compact working indicator.
- If a retry starts with a new execution identifier, stale output is replaced by the current execution output.
- When worker output finishes, the same progress channel ends streaming and signals `Reviewing result…`. The full response remains visible as static Markdown while approval is pending; the todo is not marked completed early.
- Clicking anywhere on an available todo card, including a pending card without output, opens the shared internal todo details/response overlay. The visible “Open todo details” hint and button semantics identify the action; hover, keyboard focus and press emphasize the card. Enter/Space activate the focused card.
- Details opened before the first response show “No response output yet.” and retain the canonical response holder, so subsequent streaming and review updates appear without reopening. Status is read from the current todo snapshot.
- Deleted/unavailable cards retain their readable snapshot and output but disable opening; they never navigate to a missing todo.
- If the server is unavailable, the existing application error handling remains responsible for reporting the failure.

## Tool Calls

- User-created todos use the existing Todo-panel action and have no additional tool call for card rendering.
- Model-created todos use the existing `todos` tool; rendering the card does not invoke another tool or model turn.

## Code Entry Points

- `de.heckenmann.visualagent.protocol.TodoPort`
- `de.heckenmann.visualagent.protocol.ConversationPort`
- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`
- `de.heckenmann.visualagent.ui.conversation.ConversationTodoCard`
- `de.heckenmann.visualagent.ui.conversation.ConversationScrollOnChangeEffect`
- `de.heckenmann.visualagent.ui.todo.TodoResponseSingleLine`
- `de.heckenmann.visualagent.ui.todo.TodoResponseOverlay`

## Invariants

- Todo cards are presentation-only timeline items and are excluded from model context.
- The complete card and audit timeline remains visible in the UI while the main agent
  receives only the bounded, deduplicated context projection.
- A todo card keeps one stable id and moves to its latest activity position when it is genuinely updated.
- Creation, status updates, deletion snapshots, and response progress request the newest conversation position regardless of the previous scroll position. No synthetic history message or separate todo scrolling mechanism is needed.
- Conversation messages and todo activity use one database-generated total order. Legacy rows without a sequence use timestamps and a documented deterministic fallback.
- Existing todos are updated only when the objective and scope remain the same; a different objective gets a new todo so prior history remains meaningful.
- One canonical execution stream is fanned out to Conversation, Todo, and the overlay.
- Each progress consumer applies deltas in order through a single UI collector while the provider stream is active; rendering does not wait for the final response event.
- Codex provider text is forwarded on its first native delta, including when it is the only delta; completed text is never replayed as simulated progress.
- Compact previews are bounded; the canonical full response is not truncated.
- Initial snapshot reads preserve lifecycle events received while I/O is pending: newer status/description changes and deletion snapshots take precedence per todo. Unchanged todos still load normally.
- Saved output hydrates the existing response holder in place, including when details opened before initialization completed. Any received live progress takes precedence over persisted prior output. Clearing presentation state invalidates an unfinished initial load.
- Every canonical todo state is represented by both a readable status label and its corresponding accessible status icon; deleted snapshots use the unavailable state.
- Deleting a todo never removes unrelated conversation content, and its retained snapshot survives panel reloads and application restarts.
- Review formatting corrections keep the card in `Reviewing result…`; they do not restart worker streaming. Exhausted evaluation cancels the todo with `REVIEW_FAILED`, preserving its stored worker result. Only a valid reviewer `RETRY` requests new worker work (UC-0000057).

## Related Issues

Library decision for #162: reuse the existing Compose Foundation `clickable`, interaction source and Material indication. These provide accessible click/keyboard behavior and interaction tracking; no additional library is needed (reviewed klibs.io and the AndroidX Foundation implementation).

- #253 — Live todo execution cards in conversation with shared response overlay.
- #162 — Todo progress and open behavior in conversation.
- #178 — UI/application protocol boundary.
- #444 — Separate worker streaming from review and reuse the approved main-agent response.

## Task-contract integration

Optional persisted task contracts, capability routing, scoped execution and evidence-backed acceptance are specified in [UC-0000149](uc_0000149_execute_task_contracts.md). Legacy description-only tasks retain their existing behavior. Contracted tasks carry the immutable revision through worker/reviewer requests and durable attempt records; editing scope invalidates stale completion. Existing Todo/Conversation details distinguish attempts, unchecked evidence and accepted output. No implicit agent creation or dependency scheduling is added.
