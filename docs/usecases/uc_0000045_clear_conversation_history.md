# UC-0000045: Clear Conversation History

## Goal

Let the user clear the main conversation and start again with a fresh persisted welcome message, while stopping any active work.

## Primary Actor

Desktop user.

## Preconditions

- The chat panel is visible.
- Conversation persistence is available.

## Main Flow

1. The user activates the clear conversation action.
2. The UI shows an internal confirmation modal that warns the user that active requests and all todos will be removed.
3. If confirmed, the UI cancels the active main-agent request and all running sub-agent jobs.
4. The agent manager deletes every todo and then deletes main-session history from memory and persistence.
5. A post-reset welcome message is generated and persisted.
6. The chat panel clears transient entries and known-row animation state, then
   renders the new welcome message as the fresh post-reset history.

## Result

The main conversation and its todos are reset without requiring an application restart, and no stale work continues in the background.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`
- `de.heckenmann.visualagent.ui.modal.composeModalHost`
- `de.heckenmann.visualagent.agent.AgentManager.cancelAllRunningActions`
- `de.heckenmann.visualagent.agent.AgentManager.cancelAllActiveTodos`
- `de.heckenmann.visualagent.agent.AgentManager.clearTodos`
- `de.heckenmann.visualagent.agent.AgentManager.clearHistory`
- `de.heckenmann.visualagent.agent.AgentManager.addWelcomeMessageAfterReset`
- `de.heckenmann.visualagent.agent.conversation.WelcomeMessageComposer`

## Acceptance Criteria

- Old main-session messages are removed.
- A new persisted welcome message is shown after reset when the provider is reachable.
- Active main-agent request is cancelled before clearing.
- Running sub-agent jobs are cancelled before clearing.
- All todos are deleted before the persisted conversation history is cleared.
- The confirmation modal warns the user about removing active work and todos.
- Cancelling the internal confirmation modal leaves conversation history and active work unchanged.
