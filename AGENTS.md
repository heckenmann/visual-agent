# AGENTS.md

## Build Commands

```bash
gradle build              # Build project
gradle run                # Run application
gradle copyAllDependencies # Copy deps to lib/ folder
gradle test               # Run tests
```

## Pre-Commit Quality Checks

Run before every commit:
```bash
gradle build
gradle test
```

If available, also run:
```bash
# Linting
./gradlew ktlintCheck

# Type checking
./gradlew check
```

## Prerequisites

- Java 21+
- Gradle (installed via Homebrew on macOS)
- Ollama running (`ollama serve`) for local LLM

## Project Structure

```
src/main/kotlin/com/visualagent/
├── Main.kt                    # Application entry point
├── agent/                     # LLM client, provider interface
├── config/                    # AppConfig singleton
├── knowledge/                 # SQLite KnowledgeDb
├── todo/                      # Todo model
└── ui/                        # JavaFX UI panels
    └── panels/                # SubAgents, Chat, Todo, Canvas panels
```

## Tech Stack

Kotlin, Gradle (Kotlin DSL), JavaFX 21, SQLite, Ktor HTTP client

## Key Patterns

- **AppConfig**: Singleton loaded from `src/main/resources/config/app.properties`
- **LLMProvider**: Interface for Ollama/Cloud providers in `agent/LLMProvider.kt`
- **Region inheritance**: UI panels extend `javafx.scene.layout.Region`
- **VBox orientation**: VBox is always vertical in JavaFX - no `.orientation` property

## Gotchas

1. JavaFX modules require JVM args for modular JDK:
   ```kotlin
   "--add-modules", "javafx.controls,javafx.fxml,javafx.web,javafx.graphics,javafx.media,javafx.swing,javafx.base"
   ```

2. `ollama serve` must be running before `gradle run`

3. Dependencies copied to `lib/` via `gradle copyAllDependencies`

4. Kotlinx Serialization requires explicit `@Serializable` annotation on data classes used with `Json.encodeToString/decodeFromString`

5. `json.parseToJsonElement()` returns `JsonElement` - use `.jsonObject`, `.jsonArray`, `.jsonPrimitive` extensions

## Documentation Language

All documentation and code comments are in **English**.

## Development Philosophy

**Write software you would be happy to use yourself.** Prioritize:
- Intuitive UI/UX that feels natural
- Clear, readable code that explains its intent
- Thoughtful error handling with helpful messages
- Performance that doesn't frustrate users
- Features that provide real value in daily work
