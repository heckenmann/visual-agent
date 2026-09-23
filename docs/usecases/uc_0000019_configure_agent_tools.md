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

- Tool configuration controls Internal Tool IDs. An Internal Tool ID is the stable colon-delimited identity used by configuration, UI, persistence, and audit data.
- Each enabled Internal Tool ID deterministically derives one lowercase snake-case Provider Function Name. The Provider Function Name is the only name exposed in native provider schemas and the only name a model may call.
- `agent_update` accepts provider function names in its `tools` input and translates them to registered Internal Tool IDs before persisting the configuration. The UI continues to persist Internal Tool IDs directly.

## Code Entry Points

- `de.heckenmann.visualagent.agent.AgentToolConfigService`
- `de.heckenmann.visualagent.agent.AgentConfig`
- `de.heckenmann.visualagent.agent.tools.ToolRegistry`
- `de.heckenmann.visualagent.ui.application.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.modal.ComposeContentModal`

## Acceptance Criteria

- The main agent receives only its explicit orchestration and server-owned tools, including the request-safe `context` tool; it does not receive direct file-system or terminal access.
- Sub-agent tool sets can include task-specific tools like canvas or workspace files.
- The main agent can inspect any agent's tool set via `agent:list` or `agent:show` before assigning a todo.
- Disabled tools are not exposed in provider callbacks, tool schemas, or system prompt instructions.
- Model-facing prompts and function schemas never expose Internal Tool IDs; provider function names are derived rather than persisted separately.
- Per-agent tool overrides are resolved before template defaults and are still filtered by globally disabled tools.
- Tool toggles remain separate from provider/model dropdowns so capability changes are explicit.
