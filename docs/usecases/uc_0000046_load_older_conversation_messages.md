# UC-0000046: Load Older Conversation Messages

## Goal

Let the user page older persisted conversation messages into the visible chat history.

## Primary Actor

Desktop user.

## Preconditions

- Persisted history contains messages older than the currently loaded page.
- The chat panel exposes a load-older action.

## Main Flow

1. The user requests older messages.
2. The main window delegates to the agent manager.
3. The manager loads one older page from persistence.
4. The chat panel prepends the older messages without duplicating existing rows.

## Result

Long conversations can be reviewed incrementally without loading all history on startup.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.agent.AgentManager.loadOlderHistory`
- `de.heckenmann.visualagent.knowledge.ConversationStore.getConversationMessagesPage`

## Acceptance Criteria

- History paging is deterministic.
- Already visible messages are not duplicated.
- Startup history remains bounded.
