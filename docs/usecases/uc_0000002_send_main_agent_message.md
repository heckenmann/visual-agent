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

1. The user enters text in the conversation input, which may be fixed to the panel or rendered as the latest conversation message.
2. The user toggles the sticky pin button beside the clear button to switch between the two input placements; the choice is persisted.
3. The user sends the message with the send icon button or presses Enter while the input is focused.
4. Shift+Enter inserts a newline instead of sending.
5. Before rendering the turn, the chat panel allocates distinct opaque UUIDs for
   the user entry and the assistant entry, then sends both through the
   protocol-owned conversation port.
6. The server adapter delegates the request to the agent manager.
7. The agent manager builds a request context from recent history, todo state, active provider/model, enabled tools, and runtime metadata.
8. The configured provider sends the request to the selected backend.
9. The assistant response is rendered in the conversation. If the provider cannot complete the request, a safe, actionable failure message is rendered instead.
10. User and assistant messages are persisted.

## Result

The user receives a complete response and the conversation survives application restart.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.application.VisualAgentComposeApp`
- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`
- `de.heckenmann.visualagent.protocol.ConversationPort`
- `de.heckenmann.visualagent.agent.AgentManager`
- `de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps`

## Acceptance Criteria

- Messages are sent through the configured provider.
- Pressing Enter in the conversation input sends the current message.
- Pressing Shift+Enter keeps editing and inserts a newline.
- The main-agent request includes only request-scoped context.
- Conversation turns are stored in SQLite with their caller-provided opaque
  UUIDs; IDs never encode a role or message type.
- Provider failures are persisted as an assistant message without exposing provider payloads or credentials.
- The composer remains usable for multiline editing, cancellation, and keyboard submission in both input placement modes.
