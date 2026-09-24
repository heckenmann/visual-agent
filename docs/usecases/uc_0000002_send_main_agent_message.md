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
7. The agent manager loads the complete audit history from H2, then builds a history projection from persisted dialogue and eligible execution summaries. Dialogue is retained verbatim at this stage; audit-only records remain available to the history UI but are excluded from the provider context.
8. Immediately before each provider round, the provider resolves the effective context window as the smaller of the configured session limit and the selected model's reported limit. The complete latest user message has highest priority, followed by the newest completed assistant answer and preceding answers through a maximum of ten, each with its initiating user request. The model's output capacity and the minimal `tool_help` callback are reserved; full tool schemas use only remaining capacity.
9. If history or regular tool schemas must be omitted to fit the request, the server sends a request-scoped context-reduction event. The conversation input shows a subtle warning above the field and emphasizes the send action; persisted messages are unchanged.
10. If the selected model has an authoritative capability declaration without `tools`, the provider removes all tool callbacks, tool schemas, and tool-specific prompt instructions before sending the request. Unknown or incomplete capability metadata remains enabled for compatibility.
11. The configured provider sends the request to the selected backend.
12. The assistant response passes through provider-neutral response normalization, is rendered in the conversation, and is then persisted. A leading standard assistant transport marker, including a marker joined directly to an uppercase response start, is not shown or reused as dialogue content. If the provider cannot complete the request, a safe, actionable failure message is rendered instead.
13. User and assistant messages are persisted.

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
- The main-agent request is bounded dynamically by the configured session limit, the selected model limit, exact tool-schema tokens, and either the explicit output limit or a dynamic response reserve; no fixed history allowance is used.
- Models with authoritative capability metadata that omits `tools` receive neither tool definitions nor tool-specific system instructions.
- Models with incomplete capability metadata do not lose tooling solely because capability discovery is unavailable.
- The latest user message is never discarded in favor of older conversation context.
- Audit-only records are never sent to the provider, while relevant execution failures remain visible in the compact summary.
- Conversation turns are stored in H2 with their caller-provided opaque
  UUIDs; IDs never encode a role or message type.
- Provider failures are persisted as an assistant message without exposing provider payloads or credentials.
- Provider-neutral framing markers are removed before an assistant response is rendered, persisted, or reused as model context.
- The composer remains usable for multiline editing, cancellation, and keyboard submission in both input placement modes.
