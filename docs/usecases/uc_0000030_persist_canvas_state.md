# UC-0000030: Persist Canvas State

## Goal

Persist the current editable canvas document and restore it after application restart.

## Primary Actor

Desktop user.

## Preconditions

- Canvas model changes can be serialized.
- Managed workspace persistence is available.

## Main Flow

1. The user changes the canvas.
2. Drawing model changes are serialized after each mutation.
3. The canvas document is stored as a toolkit-neutral `.canvas` JSON document under the managed workspace.
4. On service creation or restart, the stored document is loaded.
5. Figures are restored as editable objects.

## Result

Canvas contents are durable and remain editable after restart.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.canvas.InMemoryCanvasService`
- `de.heckenmann.visualagent.canvas.CanvasDocumentCodec`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService`

## Acceptance Criteria

- Restart does not clear the canvas.
- Persistence preserves object editability, not just a screenshot.
- The persisted document is independent from JavaFX, JHotDraw, AWT, and Swing.
