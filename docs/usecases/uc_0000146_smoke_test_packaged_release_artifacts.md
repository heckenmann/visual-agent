# UC-0000146: Smoke-Test Packaged Release Artifacts

## Goal

Verify actual packages on their supported operating systems without requiring a release, and attach verified assets to an existing GitHub release when requested through GitHub's release UI.

## Primary Actor

Release workflow.

## Preconditions

- The package-and-smoke workflow builds all platform artifacts and uploads them as workflow artifacts.
- Linux package tests run in Ubuntu or Fedora containers with a virtual display; macOS and Windows tests run on matching hosted runners.
- A maintainer can manually dispatch the package-and-smoke workflow for a branch (normally `master`) without an existing tag or release. Manual runs never publish assets.
- Publishing a GitHub release triggers the same package-and-smoke workflow for the associated tag. Only this path validates the tag against the project version and uploads assets after successful tests.
- No model provider credentials or configured external endpoints are required.

## Main Flow

1. The workflow downloads each package artifact produced by the release build.
2. It installs the DEB in Ubuntu and the RPM in Fedora, and launches the AppImage in Ubuntu; each Linux package is tested in its matching operating-system container.
3. It mounts and launches each macOS DMG on the matching architecture runner and installs and launches the Windows MSI on Windows.
4. It launches each platform-specific executable JAR with Java 24.
5. Each launch uses an isolated temporary user profile and server data root, requests the ordinary local startup action through an opt-in system property, and waits for the desktop readiness log marker.
   Automatic onboarding is hidden for this launch without changing its stored status, so the main workspace can be verified without provider credentials.
6. The workflow verifies that the application created a non-empty H2 database and a workspace in the isolated data root.
7. It closes the application, verifies orderly process exit, uninstalls installed packages, and removes temporary data.
8. After every package smoke test succeeds, release-triggered runs attach artifacts to the existing GitHub release through a separate workflow. Manual runs stop without uploading release assets.

## Result

Every published native installer, portable Linux package, and platform-specific JAR has passed an actual application startup check on a compatible operating system. Native packages are launched without relying on a separately installed JDK.

## Tool Calls

- None.

## Code Entry Points

- `.github/workflows/package-smoke.yml`
- `.github/workflows/release.yml`
- `scripts/smoke-release-posix.py`
- `scripts/smoke-release-windows.ps1`
- `de.heckenmann.visualagent.desktop.DesktopStartupSmokeSupport`
- `de.heckenmann.visualagent.desktop.ComposeStartupHost`
- `de.heckenmann.visualagent.desktop.ComposeStartupWindows`

## Acceptance Criteria

- Smoke tests consume the uploaded release artifacts rather than rebuilding substitute packages.
- DEB and AppImage run in Ubuntu; RPM runs in Fedora; DMGs run on matching macOS architectures; MSI runs on Windows.
- Executable JARs run with Java 24 on their target OS and architecture.
- Native package smoke tests do not require a system Java installation.
- The ready signal is emitted only after the server connection, initial layout, and onboarding state are loaded and the main window is composed with restored geometry.
- Each run uses unique temporary user and server data directories and verifies the database and workspace are created there.
- Startup waits are bounded by a timeout but succeed only on the explicit readiness signal, not elapsed time.
- Installed packages are uninstalled and all temporary artifacts are removed after success or failure.
- Any failed package smoke blocks release asset upload. The GitHub release itself is already published and may remain without assets if tests fail.
- Independent OS, architecture, and artifact smoke jobs run in parallel after the package matrix completes.
- Manual runs execute the same build and smoke jobs for the selected commit without tag/version validation or release publication.
- Asset upload is defined in a separate workflow and starts only after the package-and-smoke workflow succeeds. It never creates a release.
