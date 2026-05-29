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

### Security

**Never commit sensitive data:**
- API keys, passwords, secrets
- Authentication tokens
- Private keys
- Database credentials
- User personal information

Store sensitive data in environment variables or secure configuration files that are excluded from version control (see `.gitignore`).

## Prerequisites

- Java 21+
- Gradle 9.4.1 (installed via Homebrew on macOS)
- Ollama running (`ollama serve`) for local LLM

## Project Structure

```
src/main/kotlin/de/heckenmann/visualagent/
├── Main.kt                    # Application entry point
├── agent/
│   ├── LLMProvider.kt         # Interface: chat, stream, vision, embeddings, getModels, getModelDetails
│   ├── OllamaClient.kt        # Implements LLMProvider, connects to Ollama API
│   ├── AgentManager.kt        # Manages sub-agents, sends messages via OllamaClient
│   ├── SubAgent.kt            # SubAgent data model, AgentStatus enum
│   └── SessionEvent.kt        # Sealed interface for session-level events
├── config/
│   └── AppConfig.kt           # Singleton loaded from app.properties
├── knowledge/
│   └── KnowledgeDb.kt         # SQLite with WAL mode, busy_timeout, table creation + partial CRUD
├── todo/
│   └── Todo.kt                # Todo, Priority, Status models
└── ui/
    ├── MainWindow.kt          # FXML-based, panel switching, window controls, wires backend to UI
    ├── FxmlLoader.kt          # Type-safe FXML loading utility
    ├── StatusBar.kt           # Connection status with CSS classes
    └── panels/
        ├── SessionPanel.kt         # FXML-based, OllamaClient connected, model list + details
        ├── ChatPanel.kt            # Send handler, Enter key, setOnSendMessage callback, ChatMessage
        ├── TodoPanel.kt            # FXML-based, Add dialog, Delete, checkbox toggle, priority badges
        ├── SubAgentsPanel.kt       # Agent list built in code, CSS classes (no inline styles)
        ├── CanvasPanel.kt          # Drawing canvas built in code, CSS classes
        └── ApplicationSettingsPanel.kt  # FXML-based, theme selector, font size spinner
```

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.1.21 |
| Kotlinx Serialization JSON | 1.8.1 |
| JavaFX | 21.0.2 |
| Ktor | 2.3.7 |
| Kotlinx Coroutines | 1.7.3 |
| SQLite JDBC | 3.45.0.0 |
| Logback | 1.4.14 |
| AtlantaFX | 2.0.1 |
| Gradle | 9.4.1 (Kotlin DSL) |

## Key Patterns

- **AppConfig**: Singleton loaded from `src/main/resources/config/app.properties`
- **LLMProvider**: Interface for Ollama/Cloud providers in `agent/LLMProvider.kt` — includes `chat`, `stream`, `vision`, `embeddings`, `getModels`, `getModelDetails`
- **Region inheritance**: UI panels extend `javafx.scene.layout.Region`
- **VBox orientation**: VBox is always vertical in JavaFX — no `.orientation` property
- **FXML loading**: Panels use `FxmlLoader.load(controller, "file.fxml")` — sets controller before loading, no `fx:controller` attribute in FXML (MainWindow, SessionPanel, TodoPanel, ApplicationSettingsPanel)
- **Code-built panels**: ChatPanel and SubAgentsPanel build UI in code (no FXML)
- **Panel switching**: Navigation buttons in MainWindow swap `chatArea.center` between panels
- **CSS classes**: All styling via CSS classes (dark.css), no inline `setStyle()` calls
- **Callback wiring**: ChatPanel sends messages via `setOnSendMessage` callback wired to `AgentManager.sendMessage()` in MainWindow
- **OllamaClient integration**: SessionPanel calls `getModels()` and `getModelDetails()` via `setOllamaClient()`

## Done

1. **AtlantaFX integration** — theme selector now works with AtlantaFX Base 2.0.1, switching between dark/light themes updates application styling via CSS classes
2. **SessionPanel persistence** — model selection is now persisted to AppConfig, surviving application restarts
3. **OllamaClient error handling** — proper HTTP status checking (200 OK) with detailed error messages for network failures, timeout handling, and JSON parsing errors
4. **ChatPanel copy icons** — message context menu now displays proper copy icons: 📋 for code blocks, 📄 for plain text

## Known Bugs

1. **OllamaClient.embeddings()** uses raw string interpolation — no JSON escaping of prompt text
2. **OllamaClient.vision()** hardcodes model `"llava"` instead of using AppConfig

## Gotchas

1. JavaFX modules require JVM args for modular JDK:
   ```kotlin
   "--add-modules", "javafx.controls,javafx.fxml,javafx.web,javafx.graphics,javafx.media,javafx.swing,javafx.base"
   ```

2. `ollama serve` must be running before `gradle run`

3. Dependencies copied to `lib/` via `gradle copyAllDependencies`

4. Kotlinx Serialization requires explicit `@Serializable` annotation on data classes used with `Json.encodeToString/decodeFromString`

5. `json.parseToJsonElement()` returns `JsonElement` — use `.jsonObject`, `.jsonArray`, `.jsonPrimitive` extensions

6. **JavaFX `--module-path`** must point to `lib/` — without it you get "JavaFX Runtime components missing"

7. **Main class is `de.heckenmann.visualagent.Main`** (not `MainKt`) — because `Main` extends `Application`

8. **`PRAGMA busy_timeout=5000`** in KnowledgeDb — if stale WAL/SHM files from a crashed process cause `SQLITE_BUSY`, delete `data/visual-agent.db-wal` and `data/visual-agent.db-shm` before restarting

## Documentation Language

All documentation and code comments are in **English**.

## Code Documentation

All public APIs must have meaningful Javadoc comments:
- Classes: purpose, usage context, important constraints
- Methods: parameters, return values, exceptions, side effects
- Properties: purpose and valid value ranges

Example:
```kotlin
/**
 * LLM provider interface for chat, streaming, vision, and embedding capabilities.
 *
 * @property baseUrl The Ollama API endpoint (default: http://localhost:11434)
 * @see OllamaClient for local implementation
 * @see OllamaCloudProvider for cloud implementation
 */
interface LLMProvider {
    /**
     * Send a chat message and receive a response.
     *
     * @param messages List of conversation messages
     * @return Complete chat response from the LLM
     * @throws LLMError if the request fails or model is unavailable
     */
    suspend fun chat(messages: List<Message>): ChatResponse
}
```

## Development Philosophy

**Write software you would be happy to use yourself.** Prioritize:
- Intuitive UI/UX that feels natural
- Clear, readable code that explains its intent
- Thoughtful error handling with helpful messages
- Performance that doesn't frustrate users
- Features that provide real value in daily work