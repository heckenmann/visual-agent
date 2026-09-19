# UC-0000115: Check For Application Updates

## Goal

Allow the main agent to report the running Visual Agent version and whether a newer applicable GitHub release exists.

## Primary Actor

Main agent.

## Preconditions

- The `update:check` tool is enabled for the main agent.
- The public GitHub Releases API is reachable.

## Main Flow

1. The model calls `update:check` without preview opt-in for the stable channel.
2. Visual Agent reads its build-generated current version.
3. Visual Agent queries structured GitHub Release metadata without credentials.
4. Draft releases are ignored and preview releases are considered only when explicitly requested.
5. Visual Agent compares the versions using semantic-version ordering.
6. Visual Agent returns the current version, latest applicable version, release tag, update availability, release notes, and matching platform assets.

## Alternative Flows

- If no release exists, the tool returns the current version and no available release.
- If GitHub is unavailable or returns malformed release metadata, the tool returns a safe failure without exposing raw credentials or workspace data.
- If multiple Linux package formats match, the tool reports all candidates unless `packageType` identifies one format.

## Result

The model can answer whether an update is available without downloading or installing anything.
The desktop can use the same release service to show the current version, release notes, and
the selected package name and size before the user decides whether to download it. The release
tag and selected asset name are retained so a later download can target exactly what the user
reviewed.

## Tool Calls

- `update:check`: `{"includePrerelease":false}` for stable releases.
- `update:check`: `{"includePrerelease":true,"packageType":"linux-appimage"}` for an explicitly selected preview channel and package format.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.UpdateCheckTool`
- `de.heckenmann.visualagent.update.GitHubReleaseUpdateService`
- `de.heckenmann.visualagent.update.GitHubReleaseHttpClient`
- `de.heckenmann.visualagent.update.ReleaseAssetSelector`

## Acceptance Criteria

- The current application version is derived from the build version, not duplicated in runtime code.
- Stable checks exclude draft and pre-release releases.
- Preview checks require explicit opt-in and exclude drafts.
- Version comparison follows semantic-version ordering.
- Asset names follow the existing release workflow.
- The result includes the exact release tag and selected asset metadata used by the desktop download flow.
- The tool never downloads, installs, or replaces application files.
- No API keys, workspace paths, conversation content, or database values are sent to GitHub.

## Related Use Cases

- [UC-0000116: Download And Install An Application Update](uc_0000116_download_and_install_an_application_update.md)
