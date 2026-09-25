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
6. While the user's scroll position is at the bottom of the message list, each new chunk scrolls the conversation to the newest content.
7. If the user has scrolled up to read older messages, new chunks do not disturb the current view; instead, a scroll-to-bottom button appears.
8. Each completed assistant turn is persisted with the same stable UUID as its
   streaming row. Tool-declaring turns and their results retain explicit parent
   IDs and declaration order.
9. After the request's final assistant turn is persisted, the server emits one
   completion event for that final turn. If the user remains idle and the
   composer stays eligible, UC-0000112 may use that event to request optional
   follow-up question inspiration.

## Result

The user sees progress during longer responses, with intermediate assistant prose, tool rounds, and final answers preserved as distinct ordered turns. The user stays at the bottom by default and can temporarily read older messages without losing the current scroll position.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.LLMProvider.stream`
- `de.heckenmann.visualagent.agent.AgentManager.streamMessage`
- `de.heckenmann.visualagent.agent.text.AgentResponseCoordinator`
- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`

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
- The chat panel auto-scrolls to the bottom whenever a new message appears while the scrollbar is already near the bottom.
- A scroll-to-bottom button appears when the user scrolls up; clicking it reloads the newest messages from the database and animates back to the latest message (see UC-0000093).
