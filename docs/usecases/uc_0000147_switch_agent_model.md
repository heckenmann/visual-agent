# UC-0000147: Switch the Calling Agent's Model

## Goal

Allow an authorized agent to inspect and persist its own provider/model selection using the same
configuration paths as the user interface. Related issue: #419.

## Primary Actor

Main agent or explicitly authorized sub-agent.

## Preconditions

- The current model supports native tool calls.
- The tool is globally enabled and included in the main-agent tool set.
- Sub-agents require an explicit agent or template grant; built-in role defaults do not grant it.
- The target provider is enabled and its model is selectable in the persisted catalog.

## Main Flow

1. The agent inspects its effective provider/model with `get`.
2. It discovers enabled providers and selectable models using bounded pages.
3. It calls `set` with a model ID and, optionally, a provider ID.
4. The server obtains caller identity from trusted execution context and checks current permissions.
   Model input cannot select another agent.
5. For the main agent, the server persists the active selection through `ProviderCatalogService`
   and publishes the existing settings refresh notification. This changes the Conversation selection,
   not the provider profile's default model.
6. For a sub-agent, the server updates only its provider/model through `AgentManager`, preserving
   its tools, sampling parameters, options, and other settings.
7. The result identifies the caller and saved provider/model. Subsequent agent requests use it.
   An already constructed provider request and its tool continuation keep the original model.

## Alternate Flows

- Missing caller, unknown agent, revoked grant, or global disablement fails without mutation.
- Unknown, disabled, deprecated, blacklisted, or otherwise filtered models are rejected.
  Refresh the catalog in settings before selecting a newly available model.
- The tool does not probe arbitrary endpoints or guess capabilities from model names.
- A model without tooling may be selected. Subsequent requests obey normal capability policy;
  the user can restore the selection through Conversation settings.

## Tool Calls

- Internal Tool ID: `model:selection`; Provider Function Name: `model_selection`.
- Current selection: `{"action":"get"}`.
- Enabled providers: `{"action":"listProviders","offset":0,"limit":20}`.
- Selectable models: `{"action":"listModels","providerId":"ollama","offset":0,"limit":20}`.
- Refresh from the provider API: `{"action":"refreshModels","providerId":"ollama","offset":0,"limit":20}`.
  Persists returned model metadata and capabilities using existing catalog merge rules, preserves
  the active selection and configured filters/options, and returns a bounded selectable-model page.
  Failed API requests leave the catalog unchanged. Permissions are checked before the API request
  and again before saving its result. A provider profile changed during discovery rejects the stale
  result; retry using the current profile instead of overwriting the new configuration.
- Change own model: `{"action":"set","providerId":"ollama","modelId":"model-from-list"}`.
- Provider ID defaults to the caller's provider. Lists allow limits 1-50 and offsets 0-100000.
- Responses include IDs, display names, page totals, and `effectiveFrom: "next_agent_request"`;
  they never expose API keys, endpoint credentials, or provider options.
- Registry execution preserves lifecycle events, cancellation, timeout policy, and structured errors.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.ModelSelectionTool`
- `de.heckenmann.visualagent.agent.tools.api.ModelSelectionPort`
- `de.heckenmann.visualagent.agent.tools.ModelSelectionPortAdapter`
- `de.heckenmann.visualagent.agent.config.AgentToolConfigService`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService.setActiveSelection`
- `de.heckenmann.visualagent.agent.AgentManager.updateAgent`

## Acceptance Criteria

- Main-agent changes survive restart and are visible through the Conversation provider port.
- Sub-agent changes survive restart, affect the next request, and never change another agent.
- Global disablement and per-agent grants are rechecked when the tool executes.
- Invalid input does not mutate configuration or expose secrets.
- Existing profile defaults, runtime parameters, and historic messages remain unchanged.

## Implementation Decision

Reuse the existing Spring AI callback registry, provider catalog, and agent persistence APIs rather
than adding dependencies or provider-specific switching logic. The port is Reactor-native; legacy
synchronous configuration calls execute on bounded-elastic threads, never the UI thread.
