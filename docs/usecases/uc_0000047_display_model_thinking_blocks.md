# UC-0000047: Display Model Thinking Blocks

## Goal

Extract model `<think>...</think>` blocks and show them separately when thinking display is enabled.

## Primary Actor

Desktop user.

## Preconditions

- Thinking display is enabled in application settings.
- The model response contains one or more think blocks.

## Main Flow

1. The model returns assistant text.
2. The main window chat wiring extracts think blocks from the response.
3. Extracted thinking events are added to the chat panel.
4. The visible assistant answer is stripped of the raw think tags.

## Result

The user can inspect model thinking output without polluting the final answer text.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.MainWindowChatWiring`
- `de.heckenmann.visualagent.ui.panels.ChatPanel`
- `de.heckenmann.visualagent.config.AppConfig.thinkingEnabled`

## Acceptance Criteria

- Thinking blocks are shown only when enabled.
- Final assistant text does not contain raw `<think>` tags.
- Blank thinking blocks are ignored.
