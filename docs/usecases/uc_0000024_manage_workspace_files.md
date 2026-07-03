# UC-0000024: Manage Workspace Files

## Goal

Let the user list, rename, delete, refresh, copy metadata, and open supported managed workspace files.

## Primary Actor

Desktop user.

## Preconditions

- Managed workspace metadata exists or can be synchronized.
- The files panel is visible.

## Main Flow

1. The user opens the files panel.
2. The panel lists managed files with key metadata.
3. The user filters the visible list by search query and file type when needed.
4. The user selects an action such as rename, delete, sync, copy metadata, or open in canvas.
5. Rename opens an internal dialog instead of showing inline rename fields on every row.
6. Copy metadata places path, MIME type, size, and SHA-256 on the clipboard.
7. For canvas files, the user can open the document into the current editable canvas.
8. For delete actions, the UI shows an internal confirmation modal before removing the managed file.
9. The file service updates filesystem and metadata consistently.
10. The panel refreshes its view.

## Result

Workspace files can be managed without leaving the application.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.FilesPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeModalHost`
- `de.heckenmann.visualagent.ui.compose.ActionIconButton`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService`
- `de.heckenmann.visualagent.knowledge.WorkspaceFileStore`

## Acceptance Criteria

- Rename preserves extensions unless explicitly changed.
- Canvas documents can be reopened from the files panel.
- Delete removes both managed file and metadata after internal modal confirmation.
- Copy actions never expose raw secret values.
- Search and type filters only affect the visible list; they do not mutate workspace metadata.
