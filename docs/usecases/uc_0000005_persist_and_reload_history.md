# UC-0000005: Persist And Reload History

## Goal

Persist conversation messages and tool-call history so the user can continue after restarting the application.

## Primary Actor

Desktop user.

## Preconditions

- H2 persistence is available.
- Conversation entries can be written to and read from the conversation store.

## Main Flow

1. User, assistant, tool-call and sub-agent entries are recorded.
2. Records are stored in the database with caller-provided opaque UUID primary
   keys. A repeated write with identical data is idempotent; a conflicting
   reuse of an ID is rejected without overwriting the original record.
3. Assistant turns retain their request identity. Tool rows store a foreign-key
   parent assistant-turn ID and provider declaration order.
4. On startup, recent records are loaded together with complete assistant/tool
   groups touched by the history page.
5. The chat panel renders the restored messages, including tool and sub-agent rows.
6. The chat panel scrolls to the most recent message so the user sees the current end of the conversation.
7. Older history can be paged or searched without splitting a parent assistant
   turn from its tool rows.

The persisted timeline is also the source for main-agent context assembly. The
server selects the latest user-turn boundary, excludes records marked
`AUDIT_ONLY`, and summarizes execution events before sending them to a
provider. This projection does not change what the user can inspect in the
conversation or history tools.

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
- Assistant/tool ownership and declaration order survive restart and page-boundary expansion; legacy rows without parent IDs remain ungrouped.
- Restored rows retain their persisted UUIDs and do not play the new-message
  animation merely because history was loaded again.
