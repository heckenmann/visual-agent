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
2. The panel lists direct child folders and files in the current browser directory.
3. The user navigates with folders, breadcrumbs, or the parent action.
4. The user filters the visible files by search query and file type when needed.
5. The user selects an action such as rename, delete, sync, copy metadata, or open in canvas.
6. Rename opens an internal dialog instead of showing inline rename fields on every row.
7. Copy metadata places path, MIME type, size, and SHA-256 on the clipboard.
8. For canvas files, the user can open the document into the current editable canvas.
9. For delete actions, the UI shows an internal confirmation modal before removing the managed file.
10. The file service deletes the physical file when it still exists and always removes its metadata, including stale metadata for an already-missing file.
11. The panel refreshes its view.
12. Completed workspace mutations are added as passive system messages in the conversation; they do not start the main agent.

## Result

Workspace files can be managed without leaving the application.

## Tool Calls

- `workspace:file` with `{"action":"list"}` lists managed files and directories, including empty directories.
- `workspace:file` with `{"action":"search","query":"...","entryType":"file|directory","mimeType":"..."}` searches both by default or only the requested entry type; `mimeType` filters file matches.
- `workspace:file` with `{"action":"delete","id":"..."}` deletes a managed workspace file through the server-owned file service.
- `workspace:file` with `{"action":"deleteDirectory","path":"...","recursive":true}` deletes a directory and its descendants only when recursion is explicitly requested.

## Code Entry Points

- `de.heckenmann.visualagent.ui.files.FilesPanel`
- `de.heckenmann.visualagent.ui.modal.ComposeModalHost`
- `de.heckenmann.visualagent.ui.components.ActionIconButton`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService`
- `de.heckenmann.visualagent.knowledge.WorkspaceFileStore`

## Acceptance Criteria

- Rename preserves extensions unless explicitly changed.
- Canvas documents can be reopened from the files panel.
- Delete removes both managed file and metadata after internal modal confirmation, or removes stale metadata when the physical file is already absent. Directory deletion rejects non-empty directories unless `recursive=true` is explicit and never accepts the workspace root.
- Copy actions never expose raw secret values.
- Search and type filters only affect the visible list; they do not mutate workspace metadata.
- Active downloads show progress controls in their containing folder; opening is unavailable until completion.
- Pausing preserves the partial transfer, resuming continues it, and cancellation removes partial workspace data.
- File mutations are visible in the conversation and become model context only with the next user message.
