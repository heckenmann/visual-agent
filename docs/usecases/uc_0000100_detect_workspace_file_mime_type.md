# UC-0000100: Detect a File MIME Type Through an Authorized Root

## Goal

Allow an agent to determine a file's MIME type from content bytes through the authorized
workspace or directory-grant root.

## Primary Actor

Main agent or enabled sub-agent.

## Preconditions

- `workspace:file` is enabled for the agent.
- The target is either a managed workspace file or listed beneath an authorized grant.

## Main Flow

1. The model calls `workspace:file` action `mime` with a managed file ID or an opaque root ID and relative path.
2. The filesystem owner resolves the reference and reads only a bounded content prefix.
3. Apache Tika detects the content MIME type without trusting the filename extension or stored value.
4. The tool returns the detected type together with the managed file metadata.

## Alternate Flows

- Missing, empty, unregistered, unreadable, revoked, or invalid references return a safe actionable failure.
- A content/extension mismatch reports the content-derived type.

## Result

The model receives a compact content-derived MIME result while filesystem access remains server-owned.

## Tool Calls

- `workspace:file` action `mime` with `id` or `rootId` plus `path`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceFileTool`
- `de.heckenmann.visualagent.agent.tools.DirectoryToolPortAdapter`
- `org.apache.tika.Tika`

## Acceptance Criteria

- Registered binary, image, PDF, and text fixtures are detected from bytes.
- Filename extensions and persisted MIME values do not override Tika detection.
- Arbitrary filesystem paths, unregistered files, and ungranted roots are rejected.
- Detection is bounded and does not expose file contents or secrets.
