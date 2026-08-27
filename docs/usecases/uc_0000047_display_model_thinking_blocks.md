# UC-0000047: Display Model Thinking Blocks

## Goal

Show provider-supplied structured reasoning and legacy model `<think>...</think>` blocks separately when thinking display is enabled.

## Primary Actor

Desktop user.

## Preconditions

- Thinking display is enabled in application settings.
- The model response contains structured reasoning or one or more legacy think blocks.

## Main Flow

1. The model returns assistant text and may include structured reasoning.
2. The main window chat wiring reads structured reasoning and extracts any legacy think blocks from the response.
3. Extracted thinking events are added to the chat panel as collapsible rows.
4. A collapsed row previews the latest non-empty thinking line.
5. When expanded, the complete thinking content is rendered as Markdown.
6. The visible assistant answer is stripped of the raw think tags.

## Result

The user can inspect model thinking output without polluting the final answer text.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.application.VisualAgentComposeApp`
- `de.heckenmann.visualagent.ui.conversation.ThinkingRow`
- `de.heckenmann.visualagent.protocol.SettingsSnapshot.thinkingEnabled`

## Acceptance Criteria

- Thinking blocks are shown only when enabled.
- Thinking rows are collapsed by default and show the latest non-empty line as
  their preview.
- Expanded thinking content uses the same Markdown renderer as conversation
  answers.
- Structured reasoning is rendered without adding `<think>` tags to the assistant text.
- Final assistant text does not contain raw `<think>` tags.
- Blank thinking blocks are ignored.
