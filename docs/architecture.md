# Architecture

## Overview

Visual Agent follows a modular architecture with clear separation of concerns.

## Layer Model

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│                    (JavaFX UI Components)                   │
├─────────────────────────────────────────────────────────────┤
│                    Application Layer                        │
│              (Agent Logic, State Management)                │
├─────────────────────────────────────────────────────────────┤
│                    Domain Layer                             │
│         (Business Logic, Domain Models, Interfaces)         │
├─────────────────────────────────────────────────────────────┤
│                    Infrastructure Layer                     │
│    (Ollama Client, SQLite Repository, Browser Controller)   │
└─────────────────────────────────────────────────────────────┘
```

## Main Components

### 1. UI Layer (JavaFX)

| Component | Description |
|-----------|-------------|
| `MainWindow` | Main window with all panels |
| `ChatPanel` | Chat interface for conversations |
| `SubAgentsPanel` | Live view of all SubAgents |
| `TodoPanel` | Todo list with status display |
| `CanvasPanel` | Drawing area for visual output |
| `StatusBar` | Status bar for connections |

### 2. Agent Layer

```kotlin
interface Agent {
    val id: String
    val name: String
    val status: AgentStatus
    suspend fun execute(task: String): Result
}

data class SubAgent(
    override val id: String,
    override val name: String,
    val role: String,
    val capabilities: List<Capability>
)
```

### 3. Provider Layer

```kotlin
interface LLMProvider {
    suspend fun chat(messages: List<Message>): Response
    suspend fun stream(messages: List<Message>): Flow<ResponseChunk>
    suspend fun vision(image: ByteArray, prompt: String): Response
}

// Implementations:
// - OllamaLocalProvider (localhost:11434)
// - OllamaCloudProvider (api.ollama.com)
// - OpenAIProvider (future)
// - AnthropicProvider (future)
```

### 4. Data Layer

#### Knowledge Database (SQLite)

```sql
-- Long-term memory
CREATE TABLE long_term_memory (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    embedding BLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tags TEXT
);

-- Project knowledge
CREATE TABLE project_knowledge (
    id TEXT PRIMARY KEY,
    project_path TEXT NOT NULL,
    content_hash TEXT,
    summary TEXT,
    last_accessed TIMESTAMP
);

-- User preferences
CREATE TABLE user_preferences (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- Conversation history
CREATE TABLE conversation_history (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    messages_json TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 5. Browser Integration

```kotlin
interface BrowserController {
    suspend fun navigate(url: String)
    suspend fun screenshot(): ByteArray
    suspend fun getDomContent(): String
    suspend fun click(selector: String)
    suspend fun type(selector: String, text: String)
}
```

## Data Flow

### Process Chat Message

```
User Input → ChatPanel → Agent → LLMProvider → Ollama API
                ↓                              ↓
           TodoPanel ← Response ← LLMProvider
                ↓
         KnowledgeDb (save)
```

### Create SubAgent

```
User Request → MainWindow → AgentManager → SubAgent
                                      ↓
                              TodoManager (new task)
                                      ↓
                              KnowledgeDb (save context)
```

## Thread Model

- **JavaFX Application Thread**: UI updates
- **Coroutines Dispatcher**: Async operations
- **IO Dispatcher**: Database and network
- **Default Dispatcher**: CPU-intensive tasks

## Configuration Files

| File | Purpose |
|------|---------|
| `config/app.properties` | General settings |
| `config/ollama.properties` | Ollama configuration |
| `config/personalization.json` | User personalization |

## Security Considerations

1. **API Keys**: Store in encrypted keystore
2. **Database**: Optionally protect with password
3. **Screen Access**: User permission required (macOS: Screen Recording)
4. **Browser**: Sandbox mode for unknown sites

## Extensibility

The system is designed following the Open-Closed Principle:

- New providers by implementing `LLMProvider`
- New tools by registering in `ToolRegistry`
- New UI components by extending JavaFX `Pane`
