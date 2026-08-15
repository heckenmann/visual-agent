# UC-0000001: Start Desktop Application

## Goal

Start Visual Agent as a Compose Multiplatform desktop application with an immediate splash screen, a server session, persisted settings, database-backed stores, and the main workspace window available to the user.

## Primary Actor

Desktop user.

## Preconditions

- Java 21 or newer is available.
- Application dependencies are present.
- The SQLite database path from configuration is readable and writable.

## Main Flow

1. The user starts the application.
2. The Compose Multiplatform launcher creates the primary window and shows startup status.
3. The desktop host resolves the configured endpoint before contacting a server.
4. With no remote endpoint, it starts the local Spring Boot server in the same JVM; with a remote endpoint, it performs a TLS gRPC handshake and never falls back to local startup.
5. Spring initializes configuration, persistence, activity, and protocol port adapters.
6. The desktop host resolves the protocol application port and loads the initial workspace snapshot.
7. UI panels are wired and the primary workspace replaces the splash screen.

## Result

The user sees the Visual Agent main window and can interact with chat, session settings, todos, sub-agents, files, and canvas panels.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.desktop.DesktopMain`
- `de.heckenmann.visualagent.VisualAgentApplicationKt` (server-only entry point)
- `de.heckenmann.visualagent.VisualAgentApplication`
- `de.heckenmann.visualagent.desktop.ComposeStartupHost`
- `de.heckenmann.visualagent.desktop.LocalApplicationConnection`
- `de.heckenmann.visualagent.ui.application.VisualAgentComposeApp`

## Acceptance Criteria

- Starting via the desktop host opens a splash screen before the main window.
- Local startup does not open a TCP listener unless a remote server port is explicitly configured.
- A configured remote endpoint is never silently replaced by a local server.
- A protocol incompatibility or server failure is shown as a safe startup error with an explicit retry action.
- The main workspace is created only after the local or remote readiness handshake and initial layout snapshot succeed.
- Startup does not lose persisted runtime state.
