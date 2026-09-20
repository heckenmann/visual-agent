# Documentation

Visual Agent uses Spring Data R2DBC over embedded H2. Spring Boot Flyway applies versioned SQL resources through a JDBC-only migration connection before the R2DBC stores are created. The docs below reflect the current runtime stack.

## Table of Contents

- [Architecture](architecture.md) - System architecture, runtime flow, in-flight indicator, current constraints
- [Setup Guide](setup.md) - Installation, prerequisites, run/test commands, troubleshooting
- [API Reference](api.md) - `LLMProvider`, Spring AI integration, tool-calling contracts, activity surface
- [Database Schema](database.md) - H2 schema, indexes, persistence behavior
- [SubAgents](subagents.md) - Autonomous/sub-agent model, tool sets, autonomous loop
- [Compose Migration Audit](compose-migration-audit.md) - Per-requirement evidence for the JavaFX to Compose Multiplatform decision
- [Development Conventions](conventions.md) - Use-case traceability and documentation rules

## Quick Start

```bash
./gradlew build
./gradlew :desktop:run
```

For a standalone Spring server without the desktop host, run
`./gradlew :application:runServer`.

## Important Links

- [Spring AI Tools](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Ollama Documentation](https://docs.ollama.com)
- [Compose Multiplatform Documentation](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
