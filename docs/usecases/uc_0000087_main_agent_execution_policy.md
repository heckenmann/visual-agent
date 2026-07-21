# UC-0000087: Main Agent Execution Policy

## Goal

Define when the main agent delegates work to sub-agents, how it chooses sub-agent roles, when it searches conversation history, and how it handles failures.

## Primary Actor

Main orchestration agent.

## Preconditions

- The main agent is configured with only `agent:*` and `todos` tools.
- Sub-agents with role-based tool sets (`researcher`, `coder`, `analyst`) exist.
- The autonomous coordinator processes assigned todos.

## Main Flow

1. The user sends a request to the main agent.
2. The main agent evaluates the request against its available tools and current context.
3. If the request requires tools the main agent does not have (file operations, terminal, browser, search, canvas, history), the main agent creates a todo assigned to a suitable sub-agent.
4. If the request references information from earlier in the conversation that is not in the current context, the main agent delegates a sub-agent to search the history.
5. If the request is a simple question answerable from current context (todos, recent messages), the main agent answers directly without creating a todo.
6. If a sub-agent fails, the main agent reads the error, adjusts the instruction, and retries. After two failures, it explains the problem to the user.

## Result

The main agent reliably delegates work that requires tools it does not have, searches history when context is missing, and handles failures gracefully.

## Tool Calls

- `agent:list` — inspect available sub-agents and their tool sets
- `agent:show` — inspect a specific sub-agent's details and log
- `agent:create` — create a new sub-agent configuration
- `agent:update` — update an existing sub-agent configuration
- `agent:delete` — delete a sub-agent
- `agent:log` — read a sub-agent's work history
- `todos` — create, update, reorder, and query todos

## Code Entry Points

- `de.heckenmann.visualagent.agent.context.MainSystemPromptComposer`
- `de.heckenmann.visualagent.agent.AgentToolConfigService`
- `de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps`

## Acceptance Criteria

- The system prompt explicitly lists the main agent's available tools and the tools it does NOT have.
- The prompt contains a concrete delegation decision tree (when to delegate vs. answer directly).
- The prompt contains sub-agent role guidance with the tool sets for `researcher`, `coder`, and `analyst`.
- The prompt instructs the model to search history (via a sub-agent) when information is missing from the current context.
- The prompt contains failure-recovery rules (retry with adjusted instruction, then explain to user).
- The prompt contains todo-driven execution guidance (create todos for non-trivial requests, assign to sub-agents).
- The role-based tool sets documented in the prompt match the actual tool sets configured in `AgentToolConfigService`.
- The main agent no longer aborts with generic "I cannot do this" when the task requires sub-agent delegation.
