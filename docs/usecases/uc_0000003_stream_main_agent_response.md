# UC-0000003: Stream Main Agent Response

## Goal

Display assistant output incrementally while the provider is still generating the response.

## Primary Actor

Desktop user.

## Preconditions

- The active provider supports streaming.
- The chat panel is connected to the main window wiring.

## Main Flow

1. The user sends a message.
2. The application starts a streaming provider request.
3. The server builds the provider request from the bounded main-agent context projection rather than the unbounded audit timeline.
4. While waiting for the first visible assistant chunk, the chat panel displays a `Thinking` indicator beside the newest pending conversation content.
5. Response chunks are emitted with their provider assistant-turn identity. Separate tool-calling rounds update separate temporary assistant messages instead of being concatenated. Non-empty whitespace-only chunks, including newline-only chunks, are preserved exactly; a Markdown section boundary is added only when distinct visible sections lack existing whitespace separation.
6. Every new or updated conversation activity requests the newest position for the next list measurement, regardless of the previous scroll position. Persisted messages, pending user messages, streaming chunks, tool results, and todo cards use the same timeline observation path.
7. The user can scroll through older messages between activity updates. New activity always returns to the newest end; loading an older history page preserves the visible anchor and does not count as new activity.
8. Each completed assistant turn is persisted with the same stable UUID as its
   streaming row. Tool-declaring turns and their results retain explicit parent
   IDs and declaration order.
9. After the request's final assistant turn is persisted, the server emits one
   completion event for that final turn. If the user remains idle and the
   composer stays eligible, UC-0000112 may use that event to request optional
   follow-up question inspiration.

## Result

The user sees progress during longer responses, with intermediate assistant prose, tool rounds, and final answers preserved as distinct ordered turns. Every new activity returns the view to the latest content. Loading older history preserves its visible anchor until new activity arrives.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.LLMProvider.stream`
- `de.heckenmann.visualagent.agent.AgentManager.streamMessage`
- `de.heckenmann.visualagent.agent.text.AgentResponseCoordinator`
- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`
- `de.heckenmann.visualagent.ui.conversation.ConversationScrollOnChangeEffect`
- `de.heckenmann.visualagent.ui.conversation.conversationTimelineScrollSnapshot`

## Acceptance Criteria

- Partial chunks are visible before completion.
- Whitespace-only chunks that carry Markdown line breaks reach both the streaming UI and the persisted assistant response unchanged.
- Distinct visible provider sections render as separate Markdown blocks, while arbitrary chunks within one section remain unchanged.
- Streaming requests use the same bounded context projection as non-streaming requests.
- A visible waiting indicator is shown before the first assistant chunk, including for the first request in an empty conversation.
- The persisted conversation contains every complete assistant turn, not partial duplicates.
- Provider assistant content and structured tool calls remain associated with the same persisted assistant turn.
- Every streamed provider turn has its own stable ID; chunks, persisted rows, retries, and completion events preserve that ID.
- Tool results carry an explicit parent assistant-turn ID and declaration order, including when history is paged or restored after restart.
- Replaying a transport retry restores every assistant turn from the original request without calling the provider again.
- The chat panel always auto-scrolls to the bottom when new activity appears, including while the user was browsing older messages. No message-type or previous-position exception applies.
- Content observation and the next-measure scroll request do not suspend. A new chunk or a measured position change cannot cancel bookkeeping and replay a previously observed message.
- Auto-follow observes the actual reverse-layout timeline, including todo creation, status updates, deletion snapshots, todo output, and grouped tool results even when persisted conversation history is unchanged.
- Snapshot comparison ignores presentation-only rows, chronological index shifts, and user-message regrouping caused by older-page loading. Immutable todo response snapshots detect updates on an otherwise unchanged mutable holder.
- Loading older pages preserves the visible key and offset without triggering auto-scroll.
- A scroll-to-bottom button appears when the user scrolls up; clicking it reloads the newest messages from the database and animates back to the latest message (see UC-0000093).

## Implementation Decision

Use the existing Compose Foundation `LazyListState.requestScrollToItem(0)` API for automatic following. It schedules positioning for the next measurement, including when inserted keyed items would otherwise preserve the previous visible key. No additional scrolling library or frame-count correction is needed. See the [Compose Foundation source contract](https://android.googlesource.com/platform/frameworks/support/+/1abcb4178d48853948b9b566cabff9222d90ab69/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyListState.kt). Explicit user navigation remains separate (UC-0000093); the broader coordinator consolidation in issue #201 is not completed by this fix.
