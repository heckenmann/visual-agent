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

1. The application loads the latest user-turn boundary from the conversation store.
2. It selects dialogue records and eligible summary-source records, excluding
   audit-only lifecycle noise.
3. It groups each user turn into the user request, deduplicated execution summary,
   and final visible assistant outcome.
4. It retains recent turns newest-first under the provider token budget and never
   removes the current user request.
5. The resulting projection is sent to the provider for normal, streaming, retry,
   resume, and autonomous review requests.
6. The complete unprojected timeline remains available to the conversation UI and
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
- Context size is bounded using the configured token budget and explicit reserves.
- Initial history and full audit history remain unchanged for the UI.
