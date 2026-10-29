# UC-0000146: Smoke-Test Packaged Release Artifacts

## Goal

Verify actual packages on their supported operating systems without requiring a release, and attach verified assets to an existing GitHub release when requested through GitHub's release UI.

## Primary Actor

Release workflow.

## Preconditions

- The package-and-smoke workflow builds all platform artifacts and uploads them as workflow artifacts.
- Linux package tests run in Ubuntu or Fedora containers with a virtual display; macOS and Windows tests run on matching hosted runners.
- A maintainer can manually dispatch the package-and-smoke workflow for a branch (normally `master`) without an existing tag or release. Manual runs never publish assets.
- Publishing a GitHub release triggers the same package-and-smoke workflow for the associated tag. This path derives the Gradle project version from the tag and uploads assets only after successful tests.
- No model provider credentials or configured external endpoints are required.

## Main Flow

1. The workflow downloads each package artifact produced by the release build.
2. It installs the DEB in Ubuntu and the RPM in Fedora, and launches the AppImage in Ubuntu; each Linux package is tested in its matching operating-system container.
3. It mounts and launches each macOS DMG on the matching architecture runner and installs and launches the Windows MSI on Windows.
4. It launches each platform-specific executable JAR with Java 27.
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
- `gradle/release-version.settings.gradle.kts`
- `build.gradle.kts` (`verifyJavaToolchains`)
- `scripts/smoke-release-posix.py`
- `scripts/smoke-release-windows.ps1`
- `de.heckenmann.visualagent.desktop.DesktopStartupSmokeSupport`
- `de.heckenmann.visualagent.desktop.ComposeStartupHost`
- `de.heckenmann.visualagent.desktop.ComposeStartupWindows`

## Acceptance Criteria

- Smoke tests consume the uploaded release artifacts rather than rebuilding substitute packages.
- DEB and AppImage run in Ubuntu; RPM runs in Fedora; DMGs run on matching macOS architectures; MSI runs on Windows.
- Executable JAR smoke tests run with Java 27 on their target OS and architecture; generated application bytecode/API targets remain Java 24.
- Native package smoke tests do not require a system Java installation.
- The ready signal is emitted only after the server connection, initial layout, and onboarding state are loaded and the main window is composed with restored geometry.
- Each run uses unique temporary user and server data directories and verifies the database and workspace are created there.
- Startup waits are bounded by a timeout but succeed only on the explicit readiness signal, not elapsed time.
- Installed packages are uninstalled and all temporary artifacts are removed after success or failure.
- Any failed package smoke blocks release asset upload. The GitHub release itself is already published and may remain without assets if tests fail.
- Independent OS, architecture, and artifact smoke jobs run in parallel after the package matrix completes.
- Manual runs execute the same build and smoke jobs for the selected commit without tag/version validation or release publication.
- Asset upload is defined in a separate workflow and starts only after the package-and-smoke workflow succeeds. It never creates a release.
- Release tags must be Semantic Versions, with an optional `v` prefix, such as `v1.2.3` or `v1.2.3-beta.1+build.7`.
  The prefix is removed for Gradle; prerelease/build metadata is preserved in runtime metadata and JAR versions.
- All verification and packaging jobs receive the same `ORG_GRADLE_PROJECT_releaseTag` environment input.
  Gradle's Provider/version-catalog API overrides the shared `visual-agent` version before any module is configured;
  the source catalog remains unchanged. Local and manual smoke builds retain its checked-in default.
- Native installer versions use only `MAJOR.MINOR.PATCH` because MSI/DMG and RPM cannot all accept SemVer suffixes.
  Major/minor must be at most 255 and patch at most 65535 to fit the Windows MSI format.
  Prereleases with the same numeric core therefore share an installer version; they are not distinct native upgrade versions.
  The original tag, including suffixes, still identifies the release receiving the assets.
- Native package failures print `jpackage` output/error logs in the job log and retain those logs
  as platform-specific workflow artifacts. A diagnostic step never changes failure into success.

## Versioning Decision

Issue #503 uses Gradle's built-in [project-property Provider API](https://docs.gradle.org/current/userguide/project_properties.html)
and [catalog version overrides](https://docs.gradle.org/current/userguide/version_catalogs.html#sec:overwriting-catalog-versions)
rather than adding a versioning library or modifying the source checkout. Workflow-wide environment propagation
prevents the verification and package matrix from using different application versions. Native installer versions
follow [Compose's platform-specific restrictions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html#package-version).

Issue #505 uses the official [JDK-8380634 correction](https://github.com/openjdk/jdk/commit/0c4156d599868992cce34605bc5bd1b9a072b502),
included in [JDK 27](https://github.com/openjdk/jdk27u/blob/master/src/jdk.jpackage/macosx/classes/jdk/jpackage/internal/CFBundleVersion.java),
instead of compensating for JDK 24's obsolete rejection of zero-major macOS versions.
The `java-toolchain` catalog entry selects JDK 27 throughout compilation, tests, CI and native
tooling/runtime. The separate `java-bytecode` entry keeps Kotlin JVM/API targets and Java release
levels at 24 because the stable Kotlin compiler does not yet expose JVM-27 bytecode generation.
`verifyJavaToolchains` verifies compiler/test-launcher JDKs and Java release levels; native runtime
verification checks the same JDK. No artificial version offset, custom packager, or signed-bundle patch is used.
Regression tests preserve `v0.1.0` as application version `0.1.0`; an actual macOS DMG build
exercises the jpackage path that failed in the release, including the unchanged installer version.

CI installs Azul Zulu JDK 27 on every platform. [Temurin no longer publishes JDK 27 for Intel macOS](https://adoptium.net/news/2026/09/jdk27-macos-x64-removal),
whereas [Zulu provides JDK 27 for macOS x64 and ARM64, Linux x64, and Windows x64](https://www.azul.com/downloads/).
This preserves JDK 27 throughout the matrix rather than downgrading one architecture or dropping its package.

## Native Smoke Environment

Ubuntu containers install the distribution's `gnome-menus` package, which supplies
standard desktop menu directories required by jpackage's XDG installation scripts.
Installer/uninstaller errors are never ignored or converted into success.
Windows closes windows belonging to the exact smoke-launched process tree, including
restarted jpackage child launchers, rather than assuming the original starter owns
the window. Unrelated application instances are not targeted. Parent-process exit
and exit code remain mandatory; forceful cleanup never counts as graceful shutdown.
Deterministic PowerShell snapshot tests run before the real Windows artifact smoke.
