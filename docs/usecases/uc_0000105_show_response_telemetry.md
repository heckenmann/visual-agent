# Show response telemetry

## Goal

Show compact, safe model-response timing and token information below a completed assistant message when the active provider supplied it.

## Preconditions

- The conversation contains a completed assistant response.
- The provider supplied at least a total duration or total token count.

## Main Flow

1. The application receives a structured provider turn.
2. It persists only the allowlisted presentation telemetry with the assistant message.
3. The Conversation UI renders a small footer such as `2.4 s · 1.2k tokens`.
4. The footer is omitted when neither value is available.

## Security and Privacy

- Raw provider payloads, tool arguments, tool results, credentials, and native reasoning are not persisted in telemetry metadata.
- A provider-designated reasoning summary is persisted only while the user has enabled Reasoning in runtime settings.
- Provider telemetry is not sent back to a model as conversation context.

## Tool Calls

- None.

## Code Entry Points

- `AgentManagerConversationOps.streamMessage`
- `ResponseTelemetryMetadata`
- `SpringConversationPort`
- `ResponseTelemetryFooter`

## Acceptance Criteria

- Durations use human-readable milliseconds, seconds, or minutes.
- Token counts are compact and only displayed when available.
- The footer has an accessibility description.
- Missing telemetry does not reserve UI space.
