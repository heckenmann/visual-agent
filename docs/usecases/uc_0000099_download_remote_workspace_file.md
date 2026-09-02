# UC-0000099: Download a Remote Workspace File

## Goal

Allow an enabled agent to download a remote file of any size into the server-managed workspace.

## Primary Actor

Main agent or enabled sub-agent.

## Preconditions

- `workspace:download` is enabled for the agent.
- The source uses `http`, `https`, `ftp`, `sftp`, or `scp`.
- The destination is a workspace-relative directory.

## Main Flow

1. The model calls `workspace:download` with a source and optional directory or filename.
2. The server validates the URI, destination, and public network target.
3. The selected protocol adapter streams the response into a temporary workspace file.
4. The server detects the MIME type from a bounded prefix of the content.
5. The temporary file is atomically moved to a unique final name and registered in workspace metadata.
6. The tool returns the managed ID, relative path, MIME type, size, and SHA-256 hash.

While the transfer is active, the files panel receives server progress updates. The
user can pause, resume, or cancel it; cancellation removes the temporary workspace
file and no managed record is created.

Each lifecycle transition is also persisted as a system notification in the
conversation. Start, pause, resume, cancellation, and progress notifications are
audit-only; completion and failure records include structured path, byte, MIME,
SHA-256, validation, and status metadata where available. A completed download
notification tells the main agent that the managed file is available for subsequent
tool calls on the next user turn.

## Alternate Flows

- Missing directory defaults to `workspace/downloads`.
- Existing names receive a deterministic numeric suffix.
- Redirects, credentials embedded in sources, private network targets, unsupported protocols, failed authentication, and partial transfers are rejected without registering a file.

## Result

The downloaded file is available to existing workspace tools without exposing an arbitrary server filesystem path.

## Tool Calls

- `workspace:download` with `source`, and optional `directory` and `filename`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceDownloadTool`
- `de.heckenmann.visualagent.workspace.WorkspaceDownloadService`
- `de.heckenmann.visualagent.workspace.WorkspaceDownloadEventBus`
- `de.heckenmann.visualagent.workspace.WorkspaceDownloadNotificationService`
- `de.heckenmann.visualagent.workspace.WorkspaceDownloadTransport`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService.registerDownloadedFile`

## Acceptance Criteria

- HTTP(S), FTP, SFTP, and SCP transfers use server-owned protocol clients.
- Active transfers expose progress and pause/resume/cancel controls to the file browser.
- Start, pause, resume, cancel, failure, and completion transitions appear as conversation notifications.
- Completion notifications are included in the main-agent conversation context.
- Repeated lifecycle notifications are coalesced before entering provider context;
  actionable failures remain available.
- The default destination is `workspace/downloads`.
- Path traversal, symlink escapes, credentials, private targets, and partial files are prevented.
- Downloads are not rejected because of their size; available disk space remains the practical limit.
- The result contains only managed workspace metadata and no credentials or raw remote error details.
