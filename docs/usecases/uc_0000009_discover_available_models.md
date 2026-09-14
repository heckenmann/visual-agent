# UC-0000009: Discover Available Models

## Goal

Fetch and display provider models that the user can actually select for chat requests.

## Primary Actor

Desktop user.

## Preconditions

- Provider endpoint is reachable.
- Required provider credentials are configured when needed.

## Main Flow

1. The user refreshes model selection from the Compose settings panel or asks the onboarding wizard to discover models for a staged provider draft.
2. The provider client asks the backend for available models.
3. Provider-specific filters remove unavailable, blacklisted, or unsuitable models.
4. The onboarding path retains structured discovered metadata in its draft; it does not persist it before Finish.
5. The catalog stores discovered models while preserving configured metadata when the user saves provider settings.
6. The UI displays selectable model names and keeps existing catalog data if refresh fails.

## Result

The user sees a usable model list rather than raw provider inventory.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.LLMProvider.getModels`
- `de.heckenmann.visualagent.ui.settings.SettingsPanel`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`
- `de.heckenmann.visualagent.agent.openai.OpenAiModelCatalog`
- `de.heckenmann.visualagent.agent.openai.OpenAiModelFilter`
- `de.heckenmann.visualagent.agent.OllamaClient`

## Acceptance Criteria

- OpenAI-compatible listings exclude models that cannot be used for chat.
- Existing model metadata is preserved when discovery updates the model list.
- Errors are surfaced without losing existing selections.
- Onboarding discovery returns structured model records without inventing an identifier when the result is empty.
