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

Enforced in build:
```bash
./gradlew ktlintJavadocCheck      # public API KDoc/Javadoc guard
./gradlew locAndPackageSizeCheck  # file/package size report (warning-only)
./gradlew unusedCodeCheck         # flags removable unused private declarations
```

## Commit Conventions

- Use Conventional Commits: `type(scope): short imperative summary`
- Keep one logical change per commit.
- Prefer small, reviewable commits over broad mixed changes.
- Use present tense and imperative mood.
- Add a scope when it improves clarity, for example `ui`, `agent`, `todo`, `knowledge`, or `docs`.
- Do not mix refactors, UI work, and documentation in one commit unless they are tightly coupled.
- Examples:
  - `feat(ui): add canvas zoom and pan controls`
  - `fix(todo): persist status updates through TodoManager`
  - `refactor(knowledge): move persistence to repository stores`
  - `docs: update agent and UX documentation`

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
- Ollama running (`ollama serve`) for local LLM, or a reachable remote Ollama endpoint
- Ollama API key when the endpoint requires bearer authentication
- OpenAI API key when using the OpenAI provider

## Project Structure

```
src/main/kotlin/de/heckenmann/visualagent/
├── Main.kt                    # Application entry point
├── agent/
│   ├── LLMProvider.kt         # Interface: chat, stream, vision, embeddings, getModels, getModelDetails
│   ├── ConfiguredLLMProvider.kt # Primary LLMProvider router selected by AppConfig
│   ├── OllamaClient.kt        # Implements LLMProvider using Spring AI + Ollama ChatModel
│   ├── ollama/                # Ollama API configuration with optional bearer authentication
│   ├── openai/                # OpenAI/OpenAI-compatible Spring AI provider implementation
│   ├── AgentManager.kt        # Main orchestration: history, tools, todos, sub-agent coordination
│   ├── SubAgent.kt            # SubAgent data model, AgentStatus enum
│   └── SessionEvent.kt        # Sealed interface for session-level events
│   └── tools/
│       ├── CoreTools.kt       # ui/history/todos/context/pwd tools
│       ├── FileTools.kt       # file read/list/glob/grep/write/edit tools
│       ├── RuntimeTools.kt    # terminal/browser/search tools
│       ├── ToolRegistry.kt    # Tool registration + Spring AI callback mapping
│       └── ToolSupport.kt     # Shared parsing/path/schema helpers
├── config/
│   └── AppConfig.kt           # Singleton loaded from app.properties
├── knowledge/
│   ├── PersistenceEntities.kt # JPA entities for the SQLite schema
│   ├── PersistenceRepositories.kt # Spring Data repositories and FTS query path
│   └── PersistenceStores.kt   # Typed persistence service interfaces/implementations
├── todo/
│   └── Todo.kt                # Todo, Priority, Status models
└── ui/
    ├── MainWindow.kt          # FXML-based, panel switching, shortcuts/command palette, window controls, wires backend to UI
    ├── FxmlLoader.kt          # Type-safe FXML loading utility
    ├── StatusBar.kt           # Legacy status component (currently not wired in MainWindow)
    └── panels/
        ├── SessionPanel.kt         # FXML-based provider/model/session settings panel
        ├── ChatPanel.kt            # FXML-backed conversation panel and send callback surface
        ├── TodoPanel.kt            # FXML-based, Add dialog, Delete, checkbox toggle, priority badges
        ├── SubAgentsPanel.kt       # FXML-backed agent list with code-built cards
        ├── canvas/                 # Drawing canvas panel, toolbar, and resize support
        └── ApplicationSettingsPanel.kt  # FXML-based, theme selector, font size spinner
```

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.2.21 |
| Kotlinx Serialization JSON | 1.8.1 |
| JavaFX | 21.0.2 |
| Spring Boot | 4.1.0 |
| Spring AI | 2.0.0 |
| HTTP | Spring `RestClient` / `WebClient` |
| Kotlinx Coroutines | 1.7.3 |
| SQLite JDBC | 3.45.0.0 |
| Logback | 1.5.18 |
| AtlantaFX | 2.0.1 |
| Gradle | 9.4.1 (Kotlin DSL) |

## Key Patterns

- **AppConfig**: Singleton loaded from `src/main/resources/config/app.properties`
- **LLMProvider**: Interface for Ollama/OpenAI providers in `agent/LLMProvider.kt` — includes `chat`, `stream`, `vision`, `embeddings`, `getModels`, `getModelDetails`
- **ConfiguredLLMProvider**: Primary `LLMProvider` bean; routes calls to Ollama or OpenAI based on `AppConfig.instance.llmProvider`
- **Region inheritance**: UI panels extend `javafx.scene.layout.Region`
- **VBox orientation**: VBox is always vertical in JavaFX — no `.orientation` property
- **FXML loading**: Panels use `FxmlLoader.load(controller, "file.fxml")` — sets controller before loading, no `fx:controller` attribute in FXML (MainWindow, SessionPanel, TodoPanel, ApplicationSettingsPanel)
- **Panel composition**: ChatPanel and SubAgentsPanel use FXML shells; message rows and agent cards are built by their controllers
- **Panel switching**: Navigation buttons in MainWindow swap `chatArea.center` between panels
- **Keyboard navigation**: MainWindow supports `Cmd/Ctrl+1..6` for panel switching and `Cmd/Ctrl+K` command palette
- **CSS classes**: All styling via CSS classes (`application.css`), no inline `setStyle()` calls
- **Callback wiring**: ChatPanel sends messages via `setOnSendMessage` callback wired to `AgentManager.sendMessage()` in MainWindow
- **Provider integration**: SessionPanel calls `getModels()` and `getModelDetails()` through the primary `LLMProvider`
- **Spring AI tool-calling**: `LLMProvider.chat/stream(ChatRequestContext)` carries enabled tool IDs + metadata; provider builds request-scoped callbacks
- **Tool event flow**: all tool calls emit STARTED/FINISHED events; UI and persistence consume these events
- **DB-first state reads**: runtime context (history/todos/sub-agent data) is loaded from DB, not long-lived in-memory caches
- **Constructor DI style**: use constructor injection with direct constructor properties (`private val`/`private var`) for required dependencies; avoid passing constructor params and re-assigning them in the class body
- **Markdown parser input**: conversation message text must be passed 1:1 to the CommonMark parser; no pre-normalization, rewriting, or heuristic transformation before parsing
- **Provider settings**: provider endpoint/model/credential values are DB-backed (`llm.provider`, `ollama.local.url`, `ollama.model`, `ollama.api.key`, `openai.model`, `openai.base.url`, `openai.api.key`)
- **Provider key storage**: `ollama.api.key` and `openai.api.key` are intentionally stored plaintext in SQLite by current product decision; never expose raw keys to model context, tool output, logs, or configuration exports
- **Ollama authentication**: a non-blank `ollama.api.key` is sent as `Authorization: Bearer <key>` on synchronous and streaming requests; key changes apply immediately, while Base URL changes require restart

## Model Context Payload

The model does not receive arbitrary global state. It receives a request-scoped context assembled in `AgentManager` and the active provider prompt factory.

### Main Agent Request Context

- System prompt from `buildMainSystemContextPrompt()` including:
  - resume hint for interrupted runs
  - authoritative todo counters (`Open`, `In Progress`, `Done`, `Cancelled`, `Total`)
  - current todo list from DB
  - active provider and active model
  - execution policy/rules (tool usage, todo/status behavior, markdown output constraints)
- Optional extra system prompt from `AppConfig.instance.userModelInstruction` (session wishes, language, etc.)
- Recent conversation history from DB: max `20` messages (`INITIAL_HISTORY_LOAD_LIMIT`)
- Tool-name guard system message from the active provider factory (exact allowed function names for this request)
- Request metadata:
  - `sessionId=main`
  - `agent=main`
  - optional `requestId`
- Enabled tools from `agentToolConfigService.mainAgentTools()`

### Sub-Agent Request Context

- `subAgent.chatHistory + new turn messages`
- Metadata:
  - `agentId`
  - `agentName`
  - `agentRole`
- Enabled tools from `agentToolConfigService.toolsFor(agent)`

### Additional Notes

- Older context beyond the recent 20 messages is not auto-included.
- If older context is required, the model should use the `history` tool (`load` / `search`).

## Done

1. **Spring AI migration** — provider path uses Spring AI with native tool-calling callbacks
2. **Tool system rebuilt** — canonical tool IDs (`ui`, `history`, `todos`, file/terminal/context/pwd, browser/search placeholders) exposed via `ToolRegistry`
3. **Persistent runtime history** — conversation and tool-call entries are persisted and restored after restart
4. **Todo integration** — todo counts/lists are available through tool calls and DB-backed orchestration context
5. **SubAgents (Phase 2)** — Persistence and UI with DB-backed load/save, templates, and live status updates
6. **Markdown rendering** — conversation markdown is parsed through `commonmark` library (no hand-written parser)
7. **Quality gates** — KDoc/Javadoc and LOC/package-size checks integrated into Gradle verification flow
8. **OpenAI provider support** — `ConfiguredLLMProvider` routes between Ollama and OpenAI-compatible endpoints
9. **Secured Ollama endpoints** — optional bearer authentication is supported for all Ollama API request paths

## Known Bugs

1. **`vision()` path** in Spring AI bridge remains unimplemented and currently throws `UnsupportedOperationException`
2. **LOC modularization incomplete** — several source files are still above 300 LOC and must be split

## Gotchas

1. JavaFX modules require JVM args for modular JDK:
   ```kotlin
   "--add-modules", "javafx.controls,javafx.fxml,javafx.web,javafx.graphics,javafx.media,javafx.swing,javafx.base"
   ```

2. JavaFX rendering is configured through Prism JVM args in `build.gradle.kts`:
   - macOS: `-Dprism.order=es2,sw`
   - Windows: `-Dprism.order=d3d,es2,sw`
   - Linux/other: `-Dprism.order=es2,sw`
   - all platforms: `-Dprism.vsync=true`

   To print the selected pipeline at startup:
   ```bash
   ./gradlew run -PvisualagentPrismVerbose=true
   ```

3. `ollama serve` must be running before `gradle run`

4. Dependencies copied to `lib/` via `gradle copyAllDependencies`

5. Kotlinx Serialization requires explicit `@Serializable` annotation on data classes used with `Json.encodeToString/decodeFromString`

6. `json.parseToJsonElement()` returns `JsonElement` — use `.jsonObject`, `.jsonArray`, `.jsonPrimitive` extensions

7. **JavaFX `--module-path`** must point to `lib/` — without it you get "JavaFX Runtime components missing"

8. **Main class is `de.heckenmann.visualagent.Main`** (not `MainKt`) — because `Main` extends `Application`

9. **`PRAGMA busy_timeout=5000`** in KnowledgeDb — if stale WAL/SHM files from a crashed process cause `SQLITE_BUSY`, delete `data/visual-agent.db-wal` and `data/visual-agent.db-shm` before restarting
10. **Current LOC policy** — file LOC target is 300; package LOC target is 3000. `locAndPackageSizeCheck` reports violations as warnings (non-blocking).

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
