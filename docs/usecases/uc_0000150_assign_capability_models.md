# UC-0000150: Assign Models to Sub-Agent Capabilities

## Goal

Use independently selected provider models for distinct sub-agent capabilities without disrupting the legacy CHAT model selection.

## Primary Actor

Desktop user.

## Preconditions

- The provider catalog has one or more selectable models with declared capabilities.
- The sub-agent and provider stores are available.

## Main Flow

1. The user opens the provider settings and refreshes the model catalog.
2. Known capabilities are displayed for each discovered model; the user can correct or supplement them with explicit toggles.
3. The user saves the provider catalog and opens the details of an existing sub-agent.
4. CHAT remains enabled, while specialized features are independently granted or revoked.
5. Each enabled feature requires a model selected from providers that advertise that feature. Selections store a provider-qualified `providerId::modelId` reference.
6. The backend validates the assigned provider and model against the current catalog before persisting the agent.
7. During an image-bearing sub-agent request, VISION routing picks the configured vision model and provider. Text-only chat retains the agent's primary model.
8. During a workspace-file image-analysis tool call, the trusted sub-agent identity selects the vision model. A disabled feature or unsupported model yields an actionable error.

## Result

The agent persists the enabled feature set and feature-specific model references. Requests use the corresponding model only when the capability is enabled. All prior agent configuration records decode with CHAT enabled by default.

## Failure Cases

- Model not found, disabled, filtered, or lacking required capabilities: reject without persisting an invalid configuration.
- VISION disabled: do not invoke the vision provider.
- Provider fails during analysis: propagate a user-facing provider failure without reporting a successful result.
- Unknown provider metadata: avoid automatically granting specialized capabilities; allow an explicit user override.

## Code Entry Points

- `ModelCapability`, `OpenAiModelCapabilities`, `ProviderCatalogService`
- `AgentConfig`, `AgentCapabilityValidator`, `SubAgent`
- `WorkspaceFileTool`, `ConfiguredLLMProvider`
- `ComposeSubAgentDetailsEditor`, `ComposeModelCapabilitiesEditor`

## Acceptance Criteria

- Capability assignments survive persistence and protocol roundtrips.
- Model dropdowns exclude models without the corresponding declared capability.
- Text-only and image-bearing requests select their respective configured models.
- Agent-scoped vision calls never use an unrestricted global model silently.
- Provider credentials remain in the provider adapter; capability strings never expose API keys.
