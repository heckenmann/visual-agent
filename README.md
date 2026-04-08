# Visual Agent

A modern Kotlin-based coding agent with JavaFX UI, utilizing local and cloud LLMs via Ollama.

## Features

- 🤖 **Multi-Provider Support** - Ollama Local, Ollama Cloud, and more providers
- 🧠 **SubAgents** - Parallel agents with specialized tasks
- ✅ **Todo System** - Integrated task management with live view
- 💾 **Knowledge Database** - SQLite-based long-term knowledge storage
- 🎨 **Canvas** - Visual output and diagrams
- 🌐 **Browser Integration** - Firefox integration for web analysis
- 🖥️ **Window Access** - Screen access for visual analysis
- 👤 **Personalization** - Agent name, image, and settings

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Build System | Gradle (Kotlin DSL) |
| UI Framework | JavaFX 21 |
| Database | SQLite (embedded) |
| HTTP Client | Ktor |
| LLM Provider | Ollama API |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     MAIN WINDOW (JavaFX)                    │
├──────────────┬──────────────────┬───────────────────────────┤
│  SUBAGENTS   │     CHAT         │        TODOS              │
│   Panel      │     Panel        │        Panel              │
│  (live view) │  (conversation)  │     (live view)           │
├──────────────┴──────────────────┴───────────────────────────┤
│                    CANVAS (visual output)                   │
├─────────────────────────────────────────────────────────────┤
│              STATUS BAR (agent status, connections)         │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
   ┌─────▼─────┐      ┌──────▼──────┐     ┌──────▼──────┐
   │  OLLAMA   │      │  KNOWLEDGE  │     │   BROWSER   │
   │  CLIENT   │      │    DB       │     │ CONTROLLER  │
   │ (REST API)│      │  (SQLite)   │     │  (Firefox)  │
   └───────────┘      └─────────────┘     └─────────────┘
```

## Project Structure

```
visual-agent/
├── build.gradle.kts
├── settings.gradle.kts
├── app/
│   └── src/main/kotlin/
│       ├── Main.kt
│       ├── ui/                    # JavaFX UI components
│       ├── agent/                 # Agent logic
│       ├── todo/                  # Todo management
│       ├── knowledge/             # Knowledge database
│       ├── browser/               # Browser integration
│       └── config/                # Configuration
└── src/main/resources/
    ├── styles.css
    ├── fxml/
    └── images/
```

## Prerequisites

- Java 21 or higher
- Gradle 8.x
- Ollama (locally installed for Local provider)

## Installation

```bash
# Clone repository
git clone <repository-url>
cd visual-agent

# Install dependencies
gradle build

# Run application
gradle run
```

## Configuration

### Ollama Local

Ensure Ollama is running locally:

```bash
ollama serve
```

### Ollama Cloud

Store API key in configuration (implemented in Phase 2).

## Development

### Build Commands

```bash
# Create build
gradle build

# Run tests
gradle test

# Run application
gradle run

# Create JAR
gradle jar
```

## Roadmap

### Phase 1: Foundation (Week 1-2)
- [ ] Gradle Project Setup
- [ ] JavaFX MainWindow with CSS styling
- [ ] Ollama Local Client (REST API)
- [ ] Basic Chat (Request/Response)

### Phase 2: Core Features (Week 3-4)
- [ ] SubAgent System (UI + Backend)
- [ ] Todo Manager with SQLite
- [ ] Knowledge DB Schema + CRUD
- [ ] Personalization (Name, Image storage)

### Phase 3: Advanced Features (Week 5-6)
- [ ] Canvas for visual output
- [ ] Ollama Cloud Provider
- [ ] Streaming Responses
- [ ] Tool-Calling Interface

### Phase 4: Integration (Week 7-8)
- [ ] Firefox Browser Controller
- [ ] Screen Capture + Analysis
- [ ] Input Simulation (Robot)
- [ ] Multi-Provider Support

## License

MIT License

## Contributing

Contributions are welcome! Please create an issue or pull request.
