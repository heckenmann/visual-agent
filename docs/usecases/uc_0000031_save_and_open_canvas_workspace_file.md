# UC-0000031: Save And Open Canvas Workspace File

## Goal

Save canvas documents as managed workspace files and reopen them through the same workspace file management capabilities as other files.

## Primary Actor

Desktop user.

## Preconditions

- Canvas panel and files panel are available.
- Workspace file service is available.

## Main Flow

1. The user enters or accepts a canvas document filename in the canvas panel.
2. The user saves the current canvas to the workspace.
3. The canvas document is written under the managed workspace canvas directory.
4. Metadata is stored with the canvas MIME type and SHA-256.
5. The files panel lists the saved document.
6. The user reopens the saved canvas document from the workspace.
7. The same save/open behavior can be invoked through enabled canvas tool calls.

## Result

Canvas objects can be stored, searched, renamed, deleted, hashed, and reopened like other workspace files.

## Tool Calls

- `canvas` actions: `saveDocument`, `openDocument`.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.CanvasPanel`
- `de.heckenmann.visualagent.ui.compose.FilesPanel`
- `de.heckenmann.visualagent.ui.compose.ActionIconButton`
- `de.heckenmann.visualagent.canvas.InMemoryCanvasService`
- `de.heckenmann.visualagent.canvas.CanvasDocumentCodec`
- `de.heckenmann.visualagent.agent.tools.canvas.CanvasTool`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService.createManagedFile`

## Acceptance Criteria

- Saved canvas files use `application/vnd.visual-agent.canvas+xml`.
- Reopened documents remain editable.
- Workspace management actions apply to saved canvas files.
- Canvas files are stored as toolkit-neutral `.canvas` JSON documents.
- The UI save action is disabled until a non-blank document filename is available.
