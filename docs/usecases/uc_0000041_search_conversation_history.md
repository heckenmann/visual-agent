# UC-0000041: Search Conversation History

## Goal

Let enabled agents search persisted conversation history when older context is needed.

## Primary Actor

Enabled agent.

## Preconditions

- Conversation history exists in persistence.
- The history tool is enabled.

## Main Flow

1. The model calls the history tool with a search or load action.
2. The tool queries persisted conversation records.
3. Matching or paged records are returned with bounded content.
4. The model uses the returned context in the current request.

## Result

Older context remains accessible without injecting the full history into every request.

## Tool Calls

- `history` actions load or search persisted conversation history.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.HistoryTool`
- `de.heckenmann.visualagent.knowledge.ConversationStore`
- `de.heckenmann.visualagent.agent.conversation.AgentConversationHistoryOps`

## Acceptance Criteria

- Recent history limit remains bounded for normal requests.
- Explicit history tool calls can retrieve older context.
