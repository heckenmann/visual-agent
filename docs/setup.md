# Setup Guide

## Prerequisites

- Java 21+
- Gradle 9.4.1
- Ollama running locally (`ollama serve`)
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

The selected model is configured via session/UI settings and forwarded in provider requests.

## Persistence Runtime

- Database path defaults to `./data/visual-agent.db`
- Schema changes are applied through Flyway migrations at startup
- Hibernate validates the mapped entities, but does not generate schema in production
- Conversation search uses SQLite FTS5 with a fallback `LIKE` path

## Troubleshooting

### JavaFX module/runtime issues

Use `./gradlew run` first; the project config applies required JavaFX args.

### Ollama not reachable

Check:

```bash
curl http://localhost:11434/api/tags
```

### SQLite lock issues

If lock persists after a crash:

```bash
rm data/visual-agent.db-wal data/visual-agent.db-shm
```

Restart the app afterwards.

### Migration startup issues

If Flyway or JPA fails during startup, check the `data/visual-agent.db` file path in `src/main/resources/config/app.properties` and ensure the application can create or write to the `data/` directory.
