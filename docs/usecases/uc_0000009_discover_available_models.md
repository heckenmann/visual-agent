# UC-0000009: Discover Available Models

## Goal

Fetch and display provider models that the user can actually select for chat requests.

## Primary Actor

Desktop user.

## Preconditions

- Provider endpoint is reachable.
- Required provider credentials are configured when needed.

## Main Flow

1. The user refreshes or opens model selection.
2. The provider client asks the backend for available models.
3. Provider-specific filters remove unavailable, blacklisted, or unsuitable models.
4. The UI displays selectable model names and available details.

## Result

The user sees a usable model list rather than raw provider inventory.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.LLMProvider.getModels`
- `de.heckenmann.visualagent.agent.openai.OpenAiModelCatalog`
- `de.heckenmann.visualagent.agent.openai.OpenAiModelFilter`
- `de.heckenmann.visualagent.agent.OllamaClient`

## Acceptance Criteria

- OpenAI-compatible listings exclude models that cannot be used for chat.
- Model details are fetched where the provider supports them.
- Errors are surfaced without losing existing selections.
