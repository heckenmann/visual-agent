# UC-0000069: Use Icon-Only Buttons

## Summary
Users interact with compact action buttons that show only an icon. Hovering over a button reveals a tooltip that explains the action.

## Actors
- User

## Preconditions
- The desktop application is running.
- A panel, toolbar, navigation rail, card, or empty state with action buttons is visible.

## Main Flow
1. The user sees an action button rendered with an icon only.
2. The user hovers the button.
3. The application shows a tooltip describing the button action.
4. The user clicks the button to execute that action.

## Alternative Flows
- If a button is disabled, it still remains icon-only and its tooltip explains the action when JavaFX displays disabled-node tooltips.
- Platform dialog buttons such as OK, Cancel, and Close may retain native text labels to preserve operating-system conventions.

## Tool Calls

- None.

## Code Entry Points
- `src/main/resources/fxml/main-window.fxml`
- `src/main/resources/fxml/session-panel.fxml`
- `src/main/resources/fxml/todo-panel.fxml`
- `src/main/resources/fxml/sub-agents-panel.fxml`
- `src/main/resources/fxml/agent-card.fxml`
- `src/main/resources/fxml/application-settings.fxml`
- `de.heckenmann.visualagent.ui.panels.ChatPanelInitializer`
- `de.heckenmann.visualagent.ui.panels.FilesPanel`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasToolbar`
- `de.heckenmann.visualagent.ui.panels.ChatMessageRenderer`

## Acceptance Criteria
- User action buttons in maintained FXML layouts do not define visible `text` labels.
- Every icon-only button has a tooltip that states the action.
- The left navigation rail uses icon-only buttons with hover tooltips.
- Dialog-provided standard buttons are allowed to keep text labels.
