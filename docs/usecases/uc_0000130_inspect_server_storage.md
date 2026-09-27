# UC-0000130: Inspect Visual Agent Server Storage

## Goal

Let an enabled agent check filesystem capacity and access for storage locations used by the Visual
Agent server.

## Primary Actor

An enabled researcher or analyst sub-agent.

## Preconditions

- The `system:filesystem` tool is enabled for the requesting agent.
- The Visual Agent server has resolved its data, database, workspace, and temporary locations.

## Main Flow

1. The agent requests a storage snapshot; no arbitrary path is accepted from the model.
2. The server inspects only its data root, managed workspace, database directory when file-backed,
   and configured JVM temporary directory.
3. Standard NIO APIs read existence, access flags, filesystem-store capacity, and read-only status.
   A missing target is checked against its nearest existing parent without creating files.
4. The tool returns logical location IDs and bounded capacity values, without returning path strings.

## Result

This is a server-host diagnostic, not a report about the desktop client's filesystem. The tool does
not enumerate arbitrary mounts, create test files, write to storage, or reveal configured local
paths. A JVM-backed in-memory database has no database-directory entry.

## Tool Calls

- `system:filesystem`: inspect Visual Agent storage, for example `{}`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.SystemFilesystemTool`
- `de.heckenmann.visualagent.agent.tools.JvmFilesystemSnapshotProvider`
- `de.heckenmann.visualagent.agent.tools.FilesystemLocationProvider`
- `de.heckenmann.visualagent.agent.tools.ServerFilesystemLocationProvider`

## Acceptance Criteria

- Uses standard JVM NIO APIs and adds no dependency.
- Inspects only explicitly selected Visual Agent server locations.
- Reports total, usable, and unallocated bytes, access flags, and filesystem type where available.
- Never returns full paths, performs writes, or claims a missing directory is writable based only on
  its nearest existing parent.
- Tests use temporary directories and do not depend on host mount inventory or timing.
