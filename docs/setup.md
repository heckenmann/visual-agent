# Setup Guide

## Prerequisites

- Java 21+ (the project auto-resolves the JDK 24 toolchain locally; CI uses JDK 21 so the Foojay toolchain resolver can fetch 24).
- The Gradle wrapper version is defined centrally in `gradle/wrapper/gradle-wrapper.properties`; the CI publish workflow reads that version when bootstrapping the wrapper on its runners.
- Ollama running locally (`ollama serve`) or a reachable remote Ollama endpoint.
- SQLite is embedded and managed automatically through Spring Data JPA + Flyway.

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

The release workflow builds a JAR on every supported platform and publishes the matching asset
alongside the native packages:

- `visual-agent-linux-x64-jar.jar`
- `visual-agent-macos-arm64-jar.jar`
- `visual-agent-macos-x64-jar.jar`
- `visual-agent-windows-x64-jar.jar`

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

The Session panel configures:

- Dynamic provider profiles and runtime adapters
- Base URL and optional bearer API key
- Selected model and model catalog metadata
- Model status, context/output limits, whitelist/blacklist rules, and options

The API key is stored as `ollama.api.key` in the SQLite `user_preferences` table. It is not written to `app.properties` or configuration exports. When configured, requests include:

```http
Authorization: Bearer <key>
```

Leaving the key blank omits the `Authorization` header. Profile URL and key changes apply to subsequent requests immediately.

## Persistence Runtime

- Database path defaults to `./data/visual-agent.db`
- `application/src/main/resources/config/app.properties` is bootstrap-only and stores the database path.
- Runtime configuration is stored in SQLite `user_preferences`; normal saves do not rewrite `app.properties`.
- Imported workspace files default to `./data/workspace/`
- Files panel search covers metadata and bounded text/PDF content. The `Sync DB` action reconciles metadata with files found below the managed workspace directory.
- Editable canvas documents saved from the Canvas or Files panel are stored as regular workspace files under `./data/workspace/canvas/`.
- Schema changes are applied through Flyway migrations at startup
- Hibernate validates the mapped entities, but does not generate schema in production
- Conversation search uses SQLite FTS5 with a fallback `LIKE` path

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

### SQLite lock issues

If lock persists after a crash:

```bash
rm data/visual-agent.db-wal data/visual-agent.db-shm
```

Restart the app afterwards.

### Migration startup issues

If Flyway or JPA fails during startup, check the `data/visual-agent.db` file path in `application/src/main/resources/config/app.properties` and ensure the application can create or write to the `data/` directory.
