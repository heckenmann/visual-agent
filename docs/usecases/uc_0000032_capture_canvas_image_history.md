# UC-0000032: Capture Canvas Image History

## Goal

Allow an enabled model to capture the current canvas as an immutable PNG or JPG image and store that image in conversation history.

## Primary Actor

Enabled sub-agent.

## Preconditions

- The canvas tool is enabled for the sub-agent.
- The canvas can be rendered to an image.

## Main Flow

1. The model calls the canvas capture action with a requested format.
2. The current canvas is rendered to image bytes.
3. The image is stored as an immutable history entry.
4. Later canvas edits do not modify the stored image.

## Result

The conversation contains a durable visual snapshot that can be persisted and reloaded.

## Tool Calls

- `canvas` with action `captureImage` stores an immutable canvas image in conversation history.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.CanvasTool`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasImageCapture`
- `de.heckenmann.visualagent.ui.panels.ChatMessageRenderer`

## Acceptance Criteria

- PNG and JPG/JPEG formats are accepted.
- Captured image history entries survive restart.
- Later canvas mutations do not change existing history images.
