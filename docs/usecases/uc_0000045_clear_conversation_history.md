# UC-0000045: Clear Conversation History

## Goal

Let the user clear the main conversation and start again with a fresh persisted welcome message.

## Primary Actor

Desktop user.

## Preconditions

- The chat panel is visible.
- Conversation persistence is available.

## Main Flow

1. The user activates the clear conversation action.
2. The UI shows an internal confirmation modal.
3. If confirmed, the UI shows reset state.
4. The agent manager deletes main-session history from memory and persistence.
5. A post-reset welcome message is generated and persisted.
6. The chat panel renders the new welcome message.

## Result

The main conversation is reset without requiring an application restart.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeModalHost`
- `de.heckenmann.visualagent.agent.AgentManager.clearHistory`
- `de.heckenmann.visualagent.agent.AgentManager.addWelcomeMessageAfterReset`

## Acceptance Criteria

- Old main-session messages are removed.
- A new persisted welcome message is shown after reset.
- UI does not leave the user in a loading state.
- Cancelling the internal confirmation modal leaves conversation history unchanged.
