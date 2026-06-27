# UC-0000008: Manage Provider Profiles

## Goal

Support multiple provider profiles with model lists, credentials, generation defaults, and provider-specific options.

## Primary Actor

Desktop user.

## Preconditions

- The provider catalog store is available.
- The settings/session UI can open provider profile editing.

## Main Flow

1. The user creates or edits a provider profile.
2. The profile stores adapter type, endpoint, credential reference/value, model entries, and defaults.
3. The catalog validates and persists the profile.
4. Model resolution uses provider, model, agent, and variant settings in deterministic order.

## Result

Different agents and sessions can use different providers and model parameters.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.provider.ProviderProfile`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- Profiles survive restart.
- Option merging is deterministic.
- Raw API keys are not included in tool output, model context, exported config, or logs.
