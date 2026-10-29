# Setup Guide

## Prerequisites

- JDK 27 for development, compilation, tests, and native packaging/runtime. Point `JAVA_HOME` at JDK 27 when running Gradle; CI installs JDK 27 explicitly. Gradle can also resolve the configured toolchain automatically. No second development JDK is required.
- CI uses Azul Zulu JDK 27 across all platforms, including Intel macOS. Temurin stopped publishing macOS x64 builds starting with JDK 27; using the same supported distribution throughout avoids an architecture-specific Java-version fallback.
- The Gradle wrapper version is defined centrally in `gradle/wrapper/gradle-wrapper.properties` and is independent of the Visual Agent application version. The Wrapper JAR is intentionally excluded from version control. In a fresh clone, run `gradle wrapper --gradle-version <version from gradle-wrapper.properties> --no-daemon` once before using `./gradlew`.
- A configured model provider: Ollama (local or remote), OpenAI-compatible, Codex CLI, or Codex Direct. Ollama must be running (`ollama serve`) only when using that provider.
- H2 is embedded and accessed through Spring Data R2DBC. Versioned schema migrations run automatically before the stores are created.

## Build and Run

```bash
./gradlew build
./gradlew :desktop:run
```

## Supported Operating Systems and Release Packages

GitHub Releases provide native desktop packages. A Java installation is not required for any of
them.

| Operating system | Architecture | Release package | Installation |
| --- | --- | --- | --- |
| macOS | Apple Silicon, Intel | DMG | Open the disk image and move Visual Agent to Applications. |
| Windows | x64 | MSI | Run the installer. |
| Debian / Ubuntu Linux | x86_64 | DEB | Install with the system package manager. |
| Fedora / openSUSE / RHEL Linux | x86_64 | RPM | Install with the system package manager. |
| Linux distributions | x86_64 | AppImage | Make the file executable and start it directly; no installation is required. |

Build the portable AppImage from source on Linux x86_64 with:

```bash
./gradlew :desktop:packageAppImage
```

The task downloads pinned, SHA-256-verified copies of the official `appimagetool` and AppImage
Type 2 runtime into the Gradle build directory and writes the result to
`modules/desktop/build/compose/binaries/main/packages/`. The release workflow publishes the same
file as `visual-agent-linux-appimage.AppImage`.

Build the executable JAR staged for a GitHub release with:

```bash
./gradlew :desktop:stageReleaseJar
```

The package-and-smoke workflow builds a JAR on every supported platform; the separate release
workflow attaches the matching asset alongside the native packages to an existing GitHub release
only after all smoke checks pass:

- `visual-agent-linux-x64-jar.jar`
- `visual-agent-macos-arm64-jar.jar`
- `visual-agent-macos-x64-jar.jar`
- `visual-agent-windows-x64-jar.jar`

Before publishing, the package-and-smoke workflow installs and launches the uploaded package artifacts on
their target systems. DEB and AppImage are tested in Ubuntu containers; RPM is tested in a Fedora
container; DMGs and MSI are tested on matching macOS and Windows runners. Platform JARs are launched
with Java 27. Smoke runs use isolated temporary data and require an explicit desktop-ready signal,
database/workspace creation, and orderly shutdown. Native packages are tested without a system JDK.
These platform checks run in parallel after packaging. Maintainers can manually dispatch the
package-and-smoke workflow for `master` or another branch using GitHub's branch selector, without
creating a tag or release. Manual runs never publish assets. All jobs test the same selected commit.
Publishing a release through GitHub triggers the package-and-smoke workflow for that release's tag;
the separate asset workflow uploads the verified packages to the existing release. The release
itself is already visible while packages are being tested; a failed test leaves it without these assets.

### Release Versions

Create and publish a GitHub release with a Semantic Version tag. An optional `v` prefix is accepted;
`v1.2.3`, `v1.2.3-beta.1`, and `v1.2.3-beta.1+build.7` are valid examples.
The workflow supplies the tag to every Gradle build through `ORG_GRADLE_PROJECT_releaseTag`.
`gradle/release-version.settings.gradle.kts` validates it, removes the optional `v`, and overrides
the shared `visual-agent` catalog entry. No source file or Git commit is changed by the release build.

All application modules, executable JARs, and runtime version metadata receive the full version.
Native installer metadata uses only `MAJOR.MINOR.PATCH`; Windows MSI limits major/minor to 255
and patch to 65535. Native packaging uses JDK 27, which includes the official JDK-8380634 fix for
zero-major macOS versions. A release such as `v0.1.0` therefore remains `0.1.0` in Gradle, runtime
metadata, and native installer metadata; no artificial version offset or bundle rewrite is used.
Same-core prereleases share a native installer version, although their full application versions
and GitHub release tags remain distinct.

The single JDK is configured centrally by `java-toolchain` in `gradle/libs.versions.toml`.
Compilation, tests, CI, `jlink`, `jpackage`, the bundled runtime, and its matching `keytool` all use JDK 27.
The separate `java-bytecode` entry remains 24: Kotlin's `jvmTarget` and `-Xjdk-release`, plus Java's
`options.release`, keep both bytecode and platform API usage compatible with Java 24. This is not
a second toolchain; the stable Kotlin compiler does not yet expose a JVM-27 bytecode target.
`verifyJavaToolchains` checks every Java compiler/test launcher and the shared Java release level
during `check`; `verifyPackages` checks that the native runtime matches the same JDK.
Regular CI and executable-JAR smoke tests run on Java 27.

Local builds and manually dispatched smoke runs keep the version in `gradle/libs.versions.toml`.
To reproduce a release build locally without publishing anything:

```bash
./gradlew printProjectVersion -PreleaseTag=v1.2.3-beta.1 --no-daemon
./gradlew :desktop:stageReleaseJar -PreleaseTag=v1.2.3-beta.1 --no-daemon
```

Creating or pushing a tag alone does not start release packaging. See
[UC-0000146](usecases/uc_0000146_smoke_test_packaged_release_artifacts.md) for the full pipeline and constraints.

Each JAR requires Java 24 and must be used only on the platform for which it was built. It does
not provide native launcher or package-manager integration.

Run the Spring server without the Compose desktop host when a standalone server is
required:

```bash
./gradlew :application:runServer
```

## Quality Checks

Standard checks:

```bash
./gradlew ktlintCheck check test
```

Executable JAR and native-launcher verification is separate from `check`, but included in `build`:

```bash
./gradlew verifyPackages --no-daemon
python3 -m unittest discover -s scripts -p 'test_release_*.py'
```

The Python suite includes offline Gradle fixture builds, so generate the wrapper first. It checks
release version propagation, local/manual defaults, invalid tags, and workflow publication rules.

Additional enforced checks in current build:

- `ktlintJavadocCheck` (public declaration KDoc guard)
- `unusedCodeCheck` (flags removable unused private declarations)
- `locAndPackageSizeCheck` (blocking file/package size limit)

## Ollama Runtime

Start server:

```bash
ollama serve
```

List local models:

```bash
ollama list
```

**Settings > Providers** configures:

- Dynamic provider profiles and runtime adapters
- Base URL and optional bearer API key
- Model catalog metadata and profile defaults
- Model status, context/output limits, whitelist/blacklist rules, and options

Select the active provider/model from the searchable picker in the Conversation composer.
Conversation settings controls context limits and message behavior, not provider credentials.

Provider profiles and their API keys are persisted in H2 `user_preferences` under
`llm.provider.catalog.v1`; the legacy `ollama.api.key` preference is retained for compatibility.
Keys are not written to `app.properties` or configuration exports. When configured, requests include:

```http
Authorization: Bearer <key>
```

Leaving the key blank omits the `Authorization` header. Profile URL and key changes apply to subsequent requests immediately.

## Persistence Runtime

- Packaged and standalone server data defaults to the platform-specific server data root:
  Linux `$XDG_DATA_HOME/Visual Agent/server/` (fallback `~/.local/share/Visual Agent/server/`),
  macOS `~/Library/Application Support/Visual Agent/server/`, or Windows
  `%LOCALAPPDATA%/Visual Agent/server/`.
- The database is `<server-data-root>/visual-agent.db`; the managed workspace is
  `<server-data-root>/workspace/`.
- Override the server root with `visual-agent.server.data-root` or the database directly with
  `visual-agent.db.path` (use absolute file-backed paths). The UI never resolves or transmits
  these paths for a remote server.
- The desktop client stores pre-connection server bookmarks separately in its per-user config
  directory (`startup-servers.json`): Linux `$XDG_CONFIG_HOME/Visual Agent/` (fallback
  `~/.config/Visual Agent/`), macOS `~/Library/Preferences/Visual Agent/`, or Windows
  `%LOCALAPPDATA%/Visual Agent/`. This file contains no provider settings or secrets.
- Runtime configuration is stored in H2 `user_preferences`; it is unavailable to the UI until
  the selected server connection is ready.
- Gradle development tasks explicitly opt into repository-local `data/visual-agent.db`; installed
  packages do not depend on the process working directory.
- Files panel search covers metadata and bounded text/PDF content. The `Sync DB` action reconciles metadata with files found below the managed workspace directory.
- Editable canvas documents saved from the Canvas or Files panel are stored as regular workspace files under `<server-data-root>/workspace/canvas/`.
- Schema changes are applied by Flyway at startup and tracked in `flyway_schema_history`; R2DBC remains the runtime persistence API
- Conversation search uses bounded database queries with a safe `LIKE` path

## Troubleshooting

### Compose Multiplatform module/runtime issues

Use `./gradlew :desktop:run` first; the `:desktop` module applies the required Compose
Multiplatform arguments.

### Compose Multiplatform rendering performance

Compose Multiplatform uses its desktop rendering stack through the Compose Gradle plugin. Keep performance-sensitive UI state hoisted and avoid expensive repaint work inside semantic workspace panels.

### Ollama not reachable

Check:

```bash
curl http://localhost:11434/api/tags
```

For a secured endpoint:

```bash
curl -H "Authorization: Bearer $OLLAMA_API_KEY" \
  https://ollama.example/api/tags
```

An HTTP `401` or `403` usually indicates a missing or invalid API key, or an endpoint that expects an authentication scheme other than bearer authentication.

### H2 lock issues

If a lock persists after a crash, verify that no Visual Agent process is still running and restart the app. H2 releases its file lock when the owning process exits.

### Migration startup issues

If schema migration fails during startup, verify the resolved server data root and that the server
process can create and write its per-user directory. For an intentional legacy repository-local
store, pass its absolute path explicitly with `-Dvisual-agent.db.path=/path/to/data/visual-agent.db`.
The application never merges a legacy `./data/` directory into an existing target automatically;
keep the server stopped while copying a complete legacy database/workspace to a new empty root.
An existing H2 database receives a private snapshot under `migration-backups/` before a pending
schema migration. If snapshot creation fails, migration does not run. If migration itself fails,
keep both the original database and the snapshot and follow the version/restore procedure in
`docs/database.md`. A database written by a newer application version cannot be downgraded.
