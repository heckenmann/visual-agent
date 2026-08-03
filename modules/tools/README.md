# Tools module

`:tools` owns provider-neutral tool contracts and execution infrastructure extracted from the application composition root.

The module is intentionally a leaf: it must not depend on `:application`, `:ui`, or `:providers`. Application services are introduced through tool-owned ports and composed by `:application` in later extraction steps.

Current contracts live under `de.heckenmann.visualagent.agent.tools.api`:

- `ToolId`, `ToolDefinition`, and `ToolResult`
- `ToolCallEvent` and `ToolCallPhase`
- `ToolEventBus`

The existing application tool implementations remain temporarily in `:application` until their direct Application dependencies have been replaced by ports.
