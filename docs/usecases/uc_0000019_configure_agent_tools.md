# UC-0000019: Configure Agent Tools

## Goal

Let the user enable or disable individual tools per agent so capabilities are explicit and controllable.

## Primary Actor

Desktop user.

## Preconditions

- Tool definitions are registered.
- Agent tool configuration persistence is available.

## Main Flow

1. The user opens a sub-agent's details dialog.
2. The UI shows model/runtime controls and lists registered tools with toggles.
3. The user enables or disables tools for that agent.
4. The agent configuration is persisted.
5. Future request contexts expose only enabled tools to that agent, after global policy filtering.

## Result

Agents receive only the tools allowed by persisted configuration.

## Tool Calls

- Tool configuration controls which registered tool IDs are exposed; this use case does not require a dedicated model tool call.

## Code Entry Points

- `de.heckenmann.visualagent.agent.AgentToolConfigService`
- `de.heckenmann.visualagent.agent.AgentConfig`
- `de.heckenmann.visualagent.agent.tools.ToolRegistry`
- `de.heckenmann.visualagent.ui.compose.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeContentModal`

## Acceptance Criteria

- The main agent is limited to sub-agent definition tools (`agent:list`, `agent:show`, `agent:create`, `agent:update`, `agent:delete`, `agent:log`) only.
- Sub-agent tool sets can include task-specific tools like canvas or workspace files.
- The main agent can inspect any agent's tool set via `agent:list` or `agent:show` before assigning a todo.
- Disabled tools are not exposed in provider callbacks.
- Per-agent tool overrides are resolved before template defaults and are still filtered by globally disabled tools.
- Tool toggles remain separate from provider/model dropdowns so capability changes are explicit.
