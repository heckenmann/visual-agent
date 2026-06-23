# UC-0000070: Close Application

## Summary
Users close the entire Visual Agent desktop application from the left navigation rail.

## Actors
- User

## Preconditions
- The desktop application is running.

## Main Flow
1. The user clicks the rail close button.
2. The application runs the normal stage close path.
3. The current internal window layout is persisted by the existing close handler.
4. JavaFX exits and the Spring application context is shut down by the application stop hook.

## Alternative Flows
- If the close request is consumed by a future confirmation flow, the application remains open.
- If no internal workspace window is active, the button still closes the application.

## Tool Calls

- None.

## Code Entry Points
- `src/main/resources/fxml/main-window.fxml`
- `de.heckenmann.visualagent.ui.MainWindow`
- `de.heckenmann.visualagent.Main`

## Acceptance Criteria
- The left rail contains an icon-only close button with a tooltip.
- Clicking the button closes the entire application, not only the active internal window.
- The existing stage close handler persists the workspace window layout.
- The application stop hook closes the Spring context.
