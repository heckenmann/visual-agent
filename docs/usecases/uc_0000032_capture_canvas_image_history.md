# UC-0000032: Capture Canvas Image History

## Goal

Allow an enabled model to capture the current canvas as an immutable PNG image and store that image in conversation history.

## Primary Actor

Enabled sub-agent.

## Preconditions

- The canvas tool is enabled for the sub-agent.
- The canvas can be rendered to an image.

## Main Flow

1. The model calls the canvas capture action with a requested format.
2. The current canvas is rendered to image bytes using the current Compose Multiplatform window render scale.
3. The image is stored as an immutable history entry.
4. Later canvas edits do not modify the stored image.

## Result

The conversation contains a durable visual snapshot that can be persisted and reloaded.

## Tool Calls

- `canvas` with action `captureImage` stores an immutable canvas image in conversation history.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.canvas.CanvasTool`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- PNG format is accepted in this version; unsupported formats return a clear tool failure.
- Captured image history entries survive restart.
- Later canvas mutations do not change existing history images.
- On high-DPI displays, captured images use the Compose Multiplatform render scale instead of forcing a logical 1x snapshot.
- History image previews shrink to the available conversation row width instead of overflowing narrow workspace panels.
