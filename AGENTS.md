# AGENTS.md

## Quick Start

```bash
# Prerequisites check
java -version      # Must be Java 21+
gradle --version   # Must be Gradle 8.x
ollama list        # Ollama must be installed

# Start Ollama (required for local provider)
ollama serve

# Build and run
gradle build
gradle run
```

## Project Status

**Early stage** - Documentation only, no source code implemented yet. See [Roadmap](README.md#roadmap) for phases.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Build | Gradle (Kotlin DSL) |
| UI | JavaFX 21 |
| Database | SQLite (embedded) |
| HTTP | Ktor |
| LLM | Ollama API |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     MAIN WINDOW (JavaFX)                    │
├──────────────┬──────────────────┬───────────────────────────┤
│  SUBAGENTS   │     CHAT         │        TODOS              │
│   Panel      │     Panel        │        Panel              │
├──────────────┴──────────────────┴───────────────────────────┤
│                    CANVAS (visual output)                   │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
   ┌─────▼─────┐      ┌──────▼──────┐     ┌──────▼──────┐
   │  OLLAMA   │      │  KNOWLEDGE  │     │   BROWSER   │
   │  CLIENT   │      │    DB       │     │ CONTROLLER  │
   └───────────┘      └─────────────┘     └─────────────┘
```

## Key Directories

- `docs/` - All documentation (English)
- `src/main/kotlin/` - Application source (to be created)
- `src/main/resources/` - Config, styles, FXML, images

## Gotchas

1. **JavaFX modules** - May need JVM args in `build.gradle.kts`:
   ```kotlin
   applicationDefaultJvmArgs = listOf(
       "--add-modules", "javafx.controls,javafx.fxml,javafx.web"
   )
   ```

2. **Ollama must be running** - `ollama serve` before using local provider

3. **SQLite database path** - Default: `./data/visual-agent.db` - ensure directory exists

4. **Screen access on macOS** - Requires "Screen Recording" permission for window capture features

## Documentation Language

All documentation and code comments are in **English**.

## Next Implementation Steps

1. Create `build.gradle.kts` and `settings.gradle.kts`
2. Implement `Main.kt` with JavaFX application
3. Build Ollama client (`agent/OllamaClient.kt`)
4. Create UI panels (Chat, SubAgents, Todos, Canvas)
5. Set up SQLite database schema
