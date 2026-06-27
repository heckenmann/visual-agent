# UC-0000019: Configure Agent Tools

## Goal

Let the user enable or disable individual tools per agent so capabilities are explicit and controllable.

## Primary Actor

Desktop user.

## Preconditions

- Tool definitions are registered.
- Agent tool configuration persistence is available.

## Main Flow

1. The user opens agent or application tool settings.
2. The UI lists available tools with toggles.
3. The user enables or disables tools for an agent.
4. The configuration is persisted.
5. Future request contexts expose only enabled tools to that agent.

## Result

Agents receive only the tools allowed by persisted configuration.

## Tool Calls

- Tool configuration controls which registered tool IDs are exposed; this use case does not require a dedicated model tool call.

## Code Entry Points

- `de.heckenmann.visualagent.agent.AgentToolConfigService`
- `de.heckenmann.visualagent.agent.tools.ToolRegistry`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- The main agent is limited to orchestration tools by policy.
- Sub-agent tool sets can include task-specific tools like canvas or workspace files.
- Disabled tools are not exposed in provider callbacks.
