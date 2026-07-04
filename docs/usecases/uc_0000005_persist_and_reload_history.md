# UC-0000005: Persist And Reload History

## Goal

Persist conversation messages and tool-call history so the user can continue after restarting the application.

## Primary Actor

Desktop user.

## Preconditions

- SQLite persistence is available.
- Conversation entries can be written to and read from the conversation store.

## Main Flow

1. User, assistant, tool-call and sub-agent entries are recorded.
2. Records are stored in the database.
3. On startup, recent conversation records are loaded.
4. The chat panel renders the restored messages, including tool and sub-agent rows.
5. The chat panel scrolls to the most recent message so the user sees the current end of the conversation.
6. Older history can be paged or searched when needed.

## Result

The conversation state is durable across application restarts.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.knowledge.ConversationStore`
- `de.heckenmann.visualagent.agent.conversation.AgentConversationHistoryOps`
- `de.heckenmann.visualagent.agent.tools.HistoryTool`

## Acceptance Criteria

- Restarting the application restores recent history.
- History search and paging use database-backed records.
- Tool-call history entries remain distinguishable from normal text messages.
