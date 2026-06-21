# UC-0000031: Save And Open Canvas Workspace File

## Goal

Save canvas documents as managed workspace files and reopen them through the same workspace file management capabilities as other files.

## Primary Actor

Desktop user.

## Preconditions

- Canvas panel and files panel are available.
- Workspace file service is available.

## Main Flow

1. The user saves the current canvas to the workspace.
2. The canvas document is written under the managed workspace canvas directory.
3. Metadata is stored with the canvas MIME type and SHA-256.
4. The files panel lists the saved document.
5. The user reopens the saved canvas document from the workspace.

## Result

Canvas objects can be stored, searched, renamed, deleted, hashed, and reopened like other workspace files.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.panels.canvas.CanvasDocumentPersistence`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService.createManagedFile`
- `de.heckenmann.visualagent.ui.panels.FilesPanel`

## Acceptance Criteria

- Saved canvas files use `application/vnd.visual-agent.canvas+xml`.
- Reopened documents remain editable.
- Workspace management actions apply to saved canvas files.
