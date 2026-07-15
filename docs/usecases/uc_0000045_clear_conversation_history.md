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
2. The UI shows an internal confirmation modal that warns the user that active requests and open todos will be stopped.
3. If confirmed, the UI cancels the active main-agent request, cancels all running sub-agent jobs, and cancels every non-terminal todo.
4. The agent manager deletes main-session history from memory and persistence.
5. A post-reset welcome message is generated and persisted.
6. The chat panel renders the new welcome message.

## Result

The main conversation is reset without requiring an application restart, and no stale work continues in the background.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeModalHost`
- `de.heckenmann.visualagent.agent.AgentManager.cancelAllRunningActions`
- `de.heckenmann.visualagent.agent.AgentManager.cancelAllActiveTodos`
- `de.heckenmann.visualagent.agent.AgentManager.clearHistory`
- `de.heckenmann.visualagent.agent.AgentManager.addWelcomeMessageAfterReset`
- `de.heckenmann.visualagent.agent.conversation.WelcomeMessageComposer`

## Acceptance Criteria

- Old main-session messages are removed.
- A new persisted welcome message is shown after reset when the provider is reachable.
- Active main-agent request is cancelled before clearing.
- Running sub-agent jobs are cancelled before clearing.
- Non-terminal todos are cancelled before clearing.
- The confirmation modal warns the user about stopping active work.
- Cancelling the internal confirmation modal leaves conversation history and active work unchanged.
