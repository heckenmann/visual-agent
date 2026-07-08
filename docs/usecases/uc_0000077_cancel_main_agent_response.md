# UC-0000077: Cancel Main Agent Response

## Goal

Allow the user to interrupt a running main-agent stream and keep the partial response in the conversation history.

## Primary Actor

Desktop user.

## Preconditions

- The conversation panel is visible.
- The main agent is currently streaming a response.

## Main Flow

1. The user sends a message to the main agent.
2. The conversation panel creates a [CancellationToken] for the request and starts streaming.
3. While streaming, the send button is replaced by a cancel button.
4. The user clicks the cancel button.
5. The UI calls [CancellationToken.cancel] on the active token.
6. The provider and tool-calling loop check the token between chunks and throw [CancellationException].
7. [AgentManagerConversationOps.streamMessage] catches the exception, appends ` (cancelled)` to the partial assistant text, persists it, and updates the history.
8. The in-flight indicator removes the streaming request.

## Result

The user regains control immediately and the conversation keeps whatever the model had generated so far.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.CancellationToken`
- `de.heckenmann.visualagent.agent.AgentManager.streamMessage`
- `de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps.streamMessage`
- `de.heckenmann.visualagent.agent.OllamaClient`
- `de.heckenmann.visualagent.agent.openai.OpenAiClient`
- `de.heckenmann.visualagent.agent.ToolCallingLoop`
- `de.heckenmann.visualagent.ui.compose.ComposeConversationPanel`
- `de.heckenmann.visualagent.ui.compose.ConversationInputArea`

## Acceptance Criteria

- A cancel button is visible while the main agent is streaming.
- Clicking it stops the stream and removes the in-flight indicator for that request.
- The partial assistant message is kept and marked with ` (cancelled)`.
- `./gradlew ktlintCheck check test` passes.
- `jacocoTestCoverageVerification` (≥ 0.80 LINE) continues to pass.
