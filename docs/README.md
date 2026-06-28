# Documentation

Visual Agent now uses Spring Data JPA on SQLite with Flyway migrations for persistence. The docs below reflect the current runtime stack.

## Table of Contents

- [Architecture](architecture.md) - System architecture, runtime flow, and current constraints
- [Setup Guide](setup.md) - Installation, prerequisites, run/test commands, troubleshooting
- [API Reference](api.md) - `LLMProvider`, Spring AI integration, tool-calling contracts
- [Database Schema](database.md) - SQLite schema, indexes, persistence behavior
- [SubAgents](subagents.md) - Autonomous/sub-agent model and UI integration
- [Development Conventions](conventions.md) - Use-case traceability and documentation rules

## Quick Start

```bash
./gradlew build
./gradlew run
```

## Important Links

- [Spring AI Tools](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Ollama Documentation](https://docs.ollama.com)
- [Compose Multiplatform Documentation](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
