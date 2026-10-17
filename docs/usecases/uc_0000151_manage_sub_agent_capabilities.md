# UC-0000151: Manage Sub-Agent Capabilities

## Goal

Allow the user and main agent to grant or revoke capabilities and assign appropriate models while preserving the existing tool allowlist.

## Primary Actors

Desktop user and main orchestrator agent.

## Preconditions

- Sub-agents and provider profiles exist.
- Model capability metadata is present or has been explicitly overridden.

## Main Flow

1. The user checks the enabled capabilities in the sub-agent editor, or the model invokes the available agent creation/update function.
2. CHAT remains mandatory; VISION, AUDIO_INPUT, AUDIO_OUTPUT, IMAGE_GENERATION, EMBEDDING, and TOOLS are independently configurable.
3. Each enabled specialized capability is associated with a capable model, validated against the provider catalog.
4. The agent configuration is saved through the existing persisted sub-agent record.
5. Agent list/show functions display enabled capabilities and model assignments so the orchestrator can choose appropriate workers.
6. The system prompts advise the orchestrator to delegate specialized work only to properly configured agents.
7. Disabling a feature clears the model mapping in the UI; requests that need it fail without sending data to a model.

## Tool Calls

- `agent_create` accepts optional `enabledCapabilities` (array of names) and `capabilityModels` (object).
- `agent_update` merges supplied settings and rejects unknown capability names or incapable models.
- `agent_list` and `agent_show` report enabled features and model assignments.
- Feature references may contain `providerId::modelId` for a provider other than the agent's primary one.

## Code Entry Points

- `AgentCapabilityValidator` rejects disabled, unavailable, or incapable model assignments.
- `AgentCreateTool` and `AgentUpdateTool` validate tool-provided configuration changes.
- `SubAgentCapabilitiesEditor` and `SpringProviderPort` expose provider-qualified choices and live probes.

## Live Model Probes

- The capability editor's Test action verifies **CHAT** with a real tool-free text request and **VISION** with a small image through the selected provider/model endpoint.
- Probes are bounded to 15 seconds and never store a conversation or expose tool definitions.
- AUDIO_INPUT, AUDIO_OUTPUT, IMAGE_GENERATION, EMBEDDING, and TOOLS currently have no corresponding functional probe protocol. The UI reports this limitation as an error rather than treating model metadata as a successful test.
- Existing provider workflows determine whether specialized operations are executable. Declaring a capability alone does not enable provider operations that have not yet been implemented.

## Failure Cases

- Reject disabling mandatory CHAT.
- Reject assigning a model to a disabled or unrecognized capability.
- Reject models that are missing or incompatible in the provider catalog before updating persistent state.
- Do not create partially configured agents when model validation fails.

## Acceptance Criteria

- User toggles and tool-driven assignments persist through the existing agent configuration store.
- Agent inventories reflect capability changes.
- Feature disablement is enforced on the request and image-tool paths.
- The existing per-agent tool allowlist is preserved; model capability declarations never grant new tool permissions.
- No credentials are returned in tool responses.
