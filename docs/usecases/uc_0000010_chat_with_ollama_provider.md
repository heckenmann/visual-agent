# UC-0000010: Chat With Ollama Provider

## Goal

Use an Ollama endpoint as the active model provider for chat, streaming, embeddings, and model discovery.

## Primary Actor

Desktop user.

## Preconditions

- Ollama is running locally or a remote endpoint is reachable.
- The selected model is available on that endpoint.

## Main Flow

1. The user selects an Ollama provider profile.
2. The configured provider routes requests to the Ollama client.
3. The client builds Spring AI Ollama options from resolved model settings.
4. Chat, stream, model details, and embeddings requests are sent to Ollama.
5. Responses are converted into provider-neutral application models.

## Result

The application can run local or remote Ollama-backed sessions.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.OllamaClient`
- `de.heckenmann.visualagent.agent.OllamaToollessChat`
- `de.heckenmann.visualagent.agent.OllamaClientAuxiliary`
- `de.heckenmann.visualagent.agent.OllamaClientOps`
- `de.heckenmann.visualagent.agent.ollama.OllamaApiConfiguration`
- `de.heckenmann.visualagent.agent.OllamaPromptFactory`
- `de.heckenmann.visualagent.agent.ollama.OllamaModelCapabilities`

## Acceptance Criteria

- Ollama base URL and optional bearer key are honored.
- Base provider errors are converted to useful user-facing failures.
- Tool callbacks are request-scoped.
