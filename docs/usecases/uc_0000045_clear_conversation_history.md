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
4. The agent manager deletes every todo, then atomically invalidates main-session
   requests and deletes persisted history. In-memory history is cleared only
   after the database operation succeeds.
5. The server confirms the persisted reset before requesting a welcome message.
   The chat panel immediately clears transient entries and known-row animation
   state, reloads the cleared history, and reports that the welcome is being prepared.
6. A post-reset welcome message is generated and persisted. The panel reloads
   history again to show the new welcome message.

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
- The cleared history becomes visible before a slow provider finishes generating the welcome.
- Cancellation and reset database operations run off the UI thread.
- A failed reset does not falsely report deletion or empty the displayed conversation.
- Controls are restored even if the reset, welcome generation, or history refresh fails.
- Registered requests invalidated by a reset cannot persist late assistant or
  tool messages, even when the provider completes after cancellation.
- Sub-agent jobs and todo executions register their conversation request before
  waiting for execution. Streaming, fallback responses, immutable canvas captures,
  and completion notifications retain that identity through persistence.
- Automatic terminal reviews use a newly registered identity per invocation.
  Late review responses cannot restore history, and their activity always finishes.
- Todo pickup registers its request before claiming work; the start notification
  and worker results share that identity. A reset-rejected operation does not
  terminate the persistent pickup loop or prevent future todos from starting.
- A reset invalidates only its own session; other sessions remain writable.
