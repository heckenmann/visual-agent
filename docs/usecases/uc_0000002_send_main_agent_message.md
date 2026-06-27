# UC-0000002: Send Main Agent Message

## Goal

Allow the user to send a message to the main orchestration agent and receive a model response while preserving conversation history.

## Primary Actor

Desktop user.

## Preconditions

- A provider and model are configured.
- The chat panel is visible.
- Conversation persistence is available.

## Main Flow

1. The user enters text in the conversation input.
2. The chat panel forwards the message callback to the main window.
3. The main window delegates the request to the agent manager.
4. The agent manager builds a request context from recent history, todo state, active provider/model, enabled tools, and runtime metadata.
5. The configured provider sends the request to the selected backend.
6. The assistant response is rendered in the conversation.
7. User and assistant messages are persisted.

## Result

The user receives a complete response and the conversation survives application restart.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.agent.AgentManager`
- `de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps`

## Acceptance Criteria

- Messages are sent through the configured provider.
- The main-agent request includes only request-scoped context.
- Conversation turns are stored in SQLite.
