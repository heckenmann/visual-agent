# UC-0000118: Discover and invoke tools within a bounded context

## Goal

Let a tooling-capable model discover and invoke the tools permitted for its request when
full native tool schemas would displace higher-priority conversation history.

## Primary Actor

Main or sub-agent model.

## Preconditions

- The selected model supports tool calling or its capability is unknown.
- The request has an effective set of enabled tools after role and global policy filtering.

## Main Flow

1. The provider reserves the system instructions, complete current user request, output
   capacity, and the minimal `tool_help` schema.
2. The provider retains up to ten newest final assistant answers after the complete
   latest user request. If a full prior answer cannot fit, it is shortened and marked.
3. The provider attempts to add full native schemas without displacing that latest request
   or those ten answers; remaining capacity is used for execution summaries and older history.
4. When full schemas fit, the provider sends them with exact function-name guards and
   still includes the one-line `tool_help` instruction.
5. Otherwise, the provider sends only `tool_help` and the concise instruction:
   `To list the tools available to you, call tool_help with {"action":"list"}.`
6. The model may call `tool_help` with `action=list` to discover available functions,
   `action=show` to inspect one description and schema, or `action=call` to invoke one.
7. The help tool resolves its target against the current request's enabled tool IDs and
   executes it through the normal tool registry, preserving timeout, cancellation, and
   lifecycle handling.
8. If history or ordinary tool schemas were reduced, a request-scoped event changes the
   conversation send action to warning color and displays a subtle notice above the input.

## Result

The model keeps the most recent user intent and assistant context without silently
losing the ability to access an enabled tool. Disabled and role-ineligible tools remain
unavailable through both native callbacks and the help dispatcher.

## Tool Calls

- Provider Function Name: `tool_help`.
- List arguments: `{"action":"list"}`.
- Show arguments: `{"action":"show","name":"workspace_file"}`.
- Invoke arguments: `{"action":"call","name":"workspace_file","arguments":{"action":"list"}}`.
- Delegated tools run with their stable internal IDs for registry auditing; those IDs are
  not exposed as callable function names.

## Code Entry Points

- `de.heckenmann.visualagent.agent.RequestContextBudgeter`
- `de.heckenmann.visualagent.agent.tools.ToolHelpTool`
- `de.heckenmann.visualagent.agent.tools.ToolRegistry`
- `de.heckenmann.visualagent.agent.conversation.AgentManagerConversationStreamingOps`
- `de.heckenmann.visualagent.ui.conversation.ConversationInputArea`

## Acceptance Criteria

- The full latest user request is never silently truncated or displaced by tool schemas.
- Up to ten recent final assistant answers are prioritized after the complete latest user
  request; full schemas may use capacity ahead of execution references and older history.
- A large tool event or older history record cannot cause a newer answer to be discarded.
- The history projection does not trim records before the provider's effective model
  context window and exact callback schemas have been resolved.
- Tool-capable requests always include `tool_help` and its one-line JSON call example.
- Tool-help list, details, and delegated calls are scoped to request-enabled tools.
- A model without tool support receives neither the helper schema nor tool instructions.
- Any history or tool-schema reduction is conveyed to local and gRPC conversation clients.
- UI tests verify the warning text and warning-color send action.
