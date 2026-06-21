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
3. The user selects an action such as rename, delete, refresh, copy path/hash, or open in canvas.
4. The file service updates filesystem and metadata consistently.
5. The panel refreshes its view.

## Result

Workspace files can be managed without leaving the application.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.panels.FilesPanel`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService`
- `de.heckenmann.visualagent.knowledge.WorkspaceFileStore`

## Acceptance Criteria

- Rename preserves extensions unless explicitly changed.
- Delete removes both managed file and metadata after confirmation.
- Copy actions never expose raw secret values.
