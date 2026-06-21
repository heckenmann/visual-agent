# UC-0000030: Persist Canvas State

## Goal

Persist the current editable canvas document and restore it after application restart.

## Primary Actor

Desktop user.

## Preconditions

- Canvas model changes can be serialized.
- Preference persistence is available.

## Main Flow

1. The user changes the canvas.
2. Drawing model changes are debounced.
3. The canvas document is serialized as structured XML.
4. On panel creation or restart, the stored document is loaded.
5. Figures are restored as editable objects.

## Result

Canvas contents are durable and remain editable after restart.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.panels.canvas.CanvasDocumentPersistence`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasPanel`
- `de.heckenmann.visualagent.knowledge.PreferenceStore`

## Acceptance Criteria

- Restart does not clear the canvas.
- Persistence preserves object editability, not just a screenshot.
