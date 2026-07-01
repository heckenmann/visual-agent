# UC-0000023: Import Workspace File

## Goal

Let the user import local files into the managed workspace without exposing original external source paths to the model.

## Primary Actor

Desktop user.

## Preconditions

- Files panel or open-file toolbar action is available.
- The source file exists and is within import size limits.

## Main Flow

1. The user opens a file chooser.
2. The selected file is copied into the managed workspace imports directory.
3. Metadata is recorded with ID, relative path, MIME type, size, SHA-256, and timestamps.
4. The files panel refreshes.
5. A concise history entry can reference the imported managed file.

## Alternate Flow

1. The user enters a local path in the Files panel fallback field.
2. The user activates the import action.
3. The same workspace copy and metadata flow is executed.

## Result

The file becomes durable application workspace data with a stable ID and hash.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.workspace.WorkspaceFileService.importFile`
- `de.heckenmann.visualagent.ui.compose.FilesPanel`
- `io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher`
- `de.heckenmann.visualagent.knowledge.WorkspaceFileStore`

## Acceptance Criteria

- Imports copy bytes rather than storing external paths.
- The primary UI flow uses a Compose-compatible native file picker.
- Duplicate names are handled with generated destination names.
- SHA-256 is persisted.
