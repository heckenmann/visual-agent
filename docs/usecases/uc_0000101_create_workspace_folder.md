# UC-0000101: Create Workspace Folder

## Goal

Let a user create an empty folder in the currently open managed workspace directory.

## Primary Actor

Desktop user.

## Preconditions

- The files panel is visible.

## Main Flow

1. The user opens the files panel and navigates to a workspace folder.
2. The user selects the create-folder action.
3. The user enters a valid direct-child folder name in the dialog.
4. The server creates the directory and the browser refreshes.
5. The new folder appears immediately and the mutation is added to the conversation without starting the main agent.

## Result

The new empty folder is visible and can receive imported or downloaded files.

## Tool Calls

- `workspace:file` with `{"action":"createDirectory","parentDirectory":"...","name":"..."}` creates a managed workspace folder.

## Code Entry Points

- `de.heckenmann.visualagent.ui.files.FilesPanel`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService`
- `de.heckenmann.visualagent.agent.tools.WorkspaceFileTool`

## Acceptance Criteria

- Empty folders are visible in the file browser.
- Folder names cannot escape the managed workspace.
- The folder is created in the currently open directory.
- Creating a folder does not trigger the agent; it is available as context with the next user message.
