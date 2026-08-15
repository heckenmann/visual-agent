# UC-0000070: Close Application

## Summary
Users close the entire Visual Agent desktop application from the left navigation rail.

## Actors
- User

## Preconditions
- The desktop application is running.

## Main Flow
1. The user clicks the rail close button.
2. The desktop host begins the protocol shutdown lifecycle and cancels active work.
3. The current window size and position are persisted through the layout port.
4. The application connection and its embedded Spring application context are closed before Compose Multiplatform exits.

## Alternative Flows
- If the close request is consumed by a future confirmation flow, the application remains open.
- If no workspace panel is active, the button still closes the application.

## Tool Calls

- None.

## Code Entry Points
- `modules/desktop/src/main/kotlin/de/heckenmann/visualagent/desktop/DesktopMain.kt`
- `modules/desktop/src/main/kotlin/de/heckenmann/visualagent/desktop/ComposeStartupHost.kt`
- `de.heckenmann.visualagent.protocol.LifecyclePort`

## Acceptance Criteria
- The left rail contains an icon-only close button with a tooltip.
- Clicking the button closes the entire application, not only the active workspace panel.
- Closing persists the current window size and position through the server-owned layout port.
- The desktop host closes the application connection and embedded Spring context before exiting.
