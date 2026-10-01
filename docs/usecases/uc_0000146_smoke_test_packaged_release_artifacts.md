# UC-0000146: Smoke-Test Packaged Release Artifacts

## Goal

Verify the actual release packages on their supported operating systems before publishing a GitHub release.

## Primary Actor

Release workflow.

## Preconditions

- The release workflow has built all platform artifacts and uploaded them as workflow artifacts.
- Linux package tests run in Ubuntu or Fedora containers with a virtual display; macOS and Windows tests run on matching hosted runners.
- A maintainer can manually dispatch the package-and-smoke workflow with `smoke_only` enabled to build and test packages without publishing a release.
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
8. The GitHub release is published only after every package smoke test succeeds.

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
- Any failed package smoke blocks GitHub release publication.
- Independent OS, architecture, and artifact smoke jobs run in parallel after the package matrix completes.
- Manual smoke-only runs execute the same build and smoke jobs but skip release publication.
- Release publication is defined in a separate workflow and starts only after the package-and-smoke workflow succeeds.
