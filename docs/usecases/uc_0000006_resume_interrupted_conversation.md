# UC-0000006: Resume Interrupted Conversation

## Goal

Detect and surface interrupted work so the main agent can resume instead of silently losing context.

## Primary Actor

Desktop user.

## Preconditions

- A previous request was interrupted before normal completion.
- Conversation persistence contains enough state to detect the interruption.

## Main Flow

1. The application starts.
2. The agent manager reloads persisted conversation state.
3. The conversation operations detect an interrupted run.
4. Recovery registers a conversation request identity before checking the provider connection.
5. If the provider is reachable, the request context contains an explicit interrupted-work instruction and the agent continues from persisted history.
6. The recovered response, or a user-facing connection/failure message, is persisted with that request identity.
7. If the user clears the conversation while recovery is pending, database invalidation rejects its late response and no cleared message is restored.

## Result

Interrupted agent work is recoverable through the next request context.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.AgentManager`
- `de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps`
- `de.heckenmann.visualagent.agent.conversation.AgentConversationRecoveryOps`
- `de.heckenmann.visualagent.agent.context.MainSystemPromptComposer`

## Acceptance Criteria

- The resume hint is not global hidden state; it is request-scoped.
- Restart does not discard unfinished conversation state.
- Clearing the conversation invalidates pending recovery, including delayed connection-failure responses, without recreating deleted history.
- Cancellation is not converted into a new assistant failure message.
