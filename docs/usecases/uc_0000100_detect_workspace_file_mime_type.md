# UC-0000100: Detect a Workspace File MIME Type

## Goal

Allow an agent to determine a managed workspace file's MIME type from its content bytes.

## Primary Actor

Main agent or enabled sub-agent.

## Preconditions

- `workspace:mime` is enabled for the agent.
- The target file is registered in managed workspace metadata.

## Main Flow

1. The model calls `workspace:mime` with a managed file ID or workspace-relative path.
2. The server resolves the registered file and reads only a bounded content prefix.
3. Apache Tika detects the content MIME type without trusting the filename extension or stored value.
4. The tool returns the detected type together with the managed file metadata.

## Alternate Flows

- Missing, empty, unregistered, unreadable, or invalid paths return a safe actionable failure.
- A content/extension mismatch reports the content-derived type.

## Result

The model receives a compact content-derived MIME result while filesystem access remains server-owned.

## Tool Calls

- `workspace:mime` with `id` or `path`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceMimeTypeTool`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService.detectMimeType`
- `org.apache.tika.Tika`

## Acceptance Criteria

- Registered binary, image, PDF, and text fixtures are detected from bytes.
- Filename extensions and persisted MIME values do not override Tika detection.
- Arbitrary filesystem paths and unregistered files are rejected.
- Detection is bounded and does not expose file contents or secrets.
