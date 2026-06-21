# UC-0000011: Chat With OpenAI Provider

## Goal

Use OpenAI or an OpenAI-compatible endpoint as the active model provider.

## Primary Actor

Desktop user.

## Preconditions

- An OpenAI-compatible base URL is configured.
- A valid API key is configured when required.
- A compatible model is selected.

## Main Flow

1. The user selects an OpenAI-compatible provider profile.
2. The configured provider resolves the profile and model configuration.
3. The OpenAI client creates request-scoped Spring AI chat options and tool callbacks.
4. The request is sent to the OpenAI-compatible endpoint.
5. The response is mapped to provider-neutral chat response models.

## Result

The user can chat through OpenAI-compatible model endpoints.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.openai.OpenAiClient`
- `de.heckenmann.visualagent.agent.openai.OpenAiEndpointNormalizer`
- `de.heckenmann.visualagent.agent.openai.OpenAiPromptFactory`

## Acceptance Criteria

- API keys are sent only as provider credentials.
- Quota/auth/model errors are surfaced clearly.
- OpenAI-compatible model responses are normalized.
