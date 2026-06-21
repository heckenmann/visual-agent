# UC-0000001: Start Desktop Application

## Goal

Start Visual Agent as a JavaFX desktop application with the Spring application context, persisted settings, database-backed stores, and the main workspace window available to the user.

## Primary Actor

Desktop user.

## Preconditions

- Java 21 or newer is available.
- Application dependencies are present.
- The SQLite database path from configuration is readable and writable.

## Main Flow

1. The user starts the application.
2. The JavaFX launcher creates the Spring Boot application context.
3. Configuration and persistence services initialize.
4. The main window is loaded from FXML.
5. UI panels and backend services are wired.
6. The primary stage is shown.

## Result

The user sees the Visual Agent main window and can interact with chat, session settings, todos, sub-agents, files, and canvas panels.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.Main`
- `de.heckenmann.visualagent.VisualAgentApplication`
- `de.heckenmann.visualagent.ui.MainWindow`

## Acceptance Criteria

- Starting via `gradle run` opens the main window.
- Spring-managed services are available to JavaFX controllers.
- Startup does not lose persisted runtime state.
