# UC-0000116: Download And Install An Application Update

## Goal

Allow a user to download a verified Visual Agent release and explicitly start the
platform installer without replacing the running process or modifying user data.

## Primary Actor

User.

## Preconditions

- The connected application server can reach the public GitHub Releases API.
- A newer release and an unambiguous package asset exist for the current platform.
- The release asset provides a valid SHA-256 digest.

## Main Flow

1. The user opens the Updates section in Settings.
2. The user runs a release check and reviews the version, notes, package type, and size.
3. The user selects **Download**.
4. The server resolves the exact release tag and asset name returned by that check, streams the asset to an updates staging directory next to the database, and never writes into the workspace or database.
5. The server verifies the downloaded size and GitHub SHA-256 digest before marking the asset as staged.
6. The user selects **Start installer**.
7. The server re-verifies the staged file's size and SHA-256 digest, then starts the platform helper for MSI, DMG, DEB, RPM, or AppImage packages; unsupported archive types remain available for manual installation.
8. The user completes the installer and restarts Visual Agent when prompted.

## Alternative Flows

- A missing or malformed digest rejects the asset and removes the partial file.
- A size mismatch, interrupted download, network failure, or digest mismatch removes the partial file and leaves the running application unchanged.
- Linux with multiple package formats requires an explicit package type.
- A staged update from a previous process is not assumed to be valid after restart.
- If the previously reviewed release or asset disappears, the download is rejected instead of silently selecting a different file.
- GitHub outage or rate limiting leaves the existing installation and user data untouched.

## Result

Only a complete, digest-verified asset can be started, and the installer handoff clearly requires
a restart. The current process, SQLite database, workspace files, conversation history, and API
keys are not overwritten or transmitted.

## Tool Calls

- None. The model can use `update:check` to inspect availability, but downloading and installation remain explicit user actions.

## Code Entry Points

- `de.heckenmann.visualagent.protocol.UpdatePort`
- `de.heckenmann.visualagent.update.UpdateArtifactService`
- `de.heckenmann.visualagent.update.GitHubReleaseUpdateService`
- `de.heckenmann.visualagent.ui.settings.UpdateSettingsSection`

## Acceptance Criteria

- Downloads are staged outside the database and workspace.
- Partial files are removed after any failure.
- Assets without a valid SHA-256 digest cannot be installed.
- Size and SHA-256 verification happen before an installer is started.
- Installation is never automatic and never overwrites the running process directly.
- Settings are persisted in SQLite, including automatic-check and preview-channel preferences.
- Compose UI code and protocol models contain no Reactor types.
