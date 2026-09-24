# UC-0000106: Build bounded main-agent context

## Goal

Provide the main model with the most relevant recent dialogue and a compact,
deterministic execution summary without sacrificing the complete conversation audit
timeline shown to the user.

## Primary Actor

Main agent orchestration.

## Preconditions

- Conversation history is persisted in the application database.
- The active provider exposes a configured context length.

## Main Flow

1. The application loads persisted dialogue and eligible summary-source records from the
   conversation store; audit-only lifecycle noise is excluded.
2. It projects each user turn into the user request, individually deduplicated
   execution references, and final visible assistant outcome without applying an
   independent token limit.
3. After resolving the model-reported and configured context window, the provider
   reserves output capacity and the minimal `tool_help` schema.
4. The provider retains the complete latest user request, then up to ten final
   assistant answers in reverse recency priority. An answer too large to fit is
   shortened with an explicit truncation marker.
5. Full tool schemas are included if they fit without displacing the latest user request
   or those ten answers. Remaining capacity is then filled with execution references
   and older history. Selected messages are sent in chronological order.
6. The one-line `tool_help` example is always included for tooling-capable models. If
   full schemas would displace prioritized history, only `tool_help` is sent; the model
   can list, inspect, and invoke request-enabled tools through it.
7. When the budgeter omits history or ordinary tool schemas, a request-scoped warning
   is streamed to the conversation panel; the send action receives warning emphasis
   and a subtle notice appears above the input.
8. The resulting projection is sent to the provider for normal, streaming, retry,
   resume, and autonomous review requests.
9. The complete unprojected timeline remains available to the conversation UI and
   `history` tool.

## Result

Routine todo, tool, sub-agent, workspace, and download events no longer crowd out the
user's current intent, while all events remain auditable.

## Tool Calls

- None. Context assembly is an internal server operation.

## Code Entry Points

- `de.heckenmann.visualagent.agent.conversation.MainAgentContextAssembler`
- `de.heckenmann.visualagent.knowledge.ConversationStore`
- `de.heckenmann.visualagent.agent.AgentManagerConversationOps`

## Acceptance Criteria

- The latest user request is retained even when a turn contains hundreds of events.
- Thinking, progress, telemetry, and audit-only lifecycle records are not copied
  verbatim into provider context.
- Todo, tool, sub-agent, and workspace events are deduplicated deterministically;
  actionable failures remain visible.
- The current user message is mandatory, followed by the ten newest final assistant
  answers, then native tool schemas, execution references, and remaining history.
- A non-fitting history item does not prevent smaller, lower-priority records from
  using remaining capacity; selected records are kept in their original order.
- The current user message is never truncated. If necessary, the newest prior assistant
  answer is shortened and marked so the model retains its context.
- History projection does not apply a second approximate token budget before provider
  limits and exact tool schemas are known.
- The token budget reserves output capacity and the minimal `tool_help` callback first.
  Full schemas may displace only lower-priority execution references and older history.
- Any history or regular-schema reduction produces a UI warning without changing
  persisted conversation messages.
- Historical execution, memory, and runtime state are reference data rather than
  system instructions; they cannot override the newest user request.
- Initial history and full audit history remain unchanged for the UI.
