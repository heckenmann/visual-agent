# Setup Guide

## Prerequisites

- Java 21+
- Gradle 9.4.1
- Ollama running locally (`ollama serve`) or a reachable remote Ollama endpoint
- SQLite is embedded and managed automatically through Spring Data JPA + Flyway

## Build and Run

```bash
./gradlew build
./gradlew run
```

## Quality Checks

Standard checks:

```bash
./gradlew build
./gradlew test
./gradlew check
./gradlew ktlintCheck
```

Additional enforced checks in current build:

- `ktlintJavadocCheck` (public declaration KDoc guard)
- `unusedCodeCheck` (flags removable unused private declarations)
- `locAndPackageSizeCheck` (file/package size report; warning-only while modularization is in progress)

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
- `src/main/resources/config/app.properties` is bootstrap-only and stores the database path.
- Runtime configuration is stored in SQLite `user_preferences`; normal saves do not rewrite `app.properties`.
- Imported workspace files default to `./data/workspace/`
- Files panel search covers metadata and bounded text/PDF content. The `Sync DB` action reconciles metadata with files found below the managed workspace directory.
- Editable canvas documents saved from the Canvas or Files panel are stored as regular workspace files under `./data/workspace/canvas/`.
- Schema changes are applied through Flyway migrations at startup
- Hibernate validates the mapped entities, but does not generate schema in production
- Conversation search uses SQLite FTS5 with a fallback `LIKE` path

## Troubleshooting

### JavaFX module/runtime issues

Use `./gradlew run` first; the project config applies required JavaFX args.

### JavaFX rendering performance

The Gradle launcher prefers hardware-accelerated JavaFX Prism pipelines and keeps the software renderer as fallback:

- macOS: `es2,sw`
- Windows: `d3d,es2,sw`
- Linux/other: `es2,sw`

Vertical sync is enabled through `-Dprism.vsync=true`. To verify the selected pipeline during startup, run:

```bash
./gradlew run -PvisualagentPrismVerbose=true
```

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

If Flyway or JPA fails during startup, check the `data/visual-agent.db` file path in `src/main/resources/config/app.properties` and ensure the application can create or write to the `data/` directory.
