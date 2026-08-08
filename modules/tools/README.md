# Tools module

`:tools` owns provider-neutral tool contracts, implementations, and execution infrastructure extracted from the application composition root.

The module is intentionally a leaf: it must not depend on `:application`, `:ui`, or `:providers`. Application services are introduced through tool-owned ports and composed by `:application`.

Concrete tools are annotated with `@AgentTool`, a Spring component stereotype. Spring is a compile-only dependency of this module; the consuming `:application` runtime supplies Spring and discovers the tool beans. UI integrations remain explicit and do not use tool discovery or reflection.

Current contracts live under `de.heckenmann.visualagent.agent.tools.api`:

- `ToolId`, `ToolDefinition`, and `ToolResult`
- `ToolCallEvent` and `ToolCallPhase`
- `ToolEventBus`
- tool-owned ports for application services

`ToolDefinition` remains the runtime source of model-facing descriptions and input schemas. `@AgentTool` only controls Spring discovery.
