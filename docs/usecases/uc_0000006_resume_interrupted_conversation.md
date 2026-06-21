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
4. A resume hint is included in the next main-agent system context.
5. The agent can continue from the persisted state.

## Result

Interrupted agent work is recoverable through the next request context.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.AgentManager`
- `de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps`
- `de.heckenmann.visualagent.agent.context.MainSystemPromptComposer`

## Acceptance Criteria

- The resume hint is not global hidden state; it is request-scoped.
- Restart does not discard unfinished conversation state.
