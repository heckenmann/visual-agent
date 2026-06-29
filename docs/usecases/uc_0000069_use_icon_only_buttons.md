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
- If a button is disabled, it still remains icon-only and its tooltip explains the action when Compose Multiplatform displays disabled-node tooltips.
- Platform dialog buttons such as OK, Cancel, and Close may retain native text labels to preserve operating-system conventions.

## Tool Calls

- None.

## Code Entry Points
- `de.heckenmann.visualagent.ui.compose.ActionIconButton`
- `de.heckenmann.visualagent.ui.compose.ActionTooltip`
- `de.heckenmann.visualagent.ui.compose.ComposeRail`
- `de.heckenmann.visualagent.ui.compose.ComposeSplitWorkspace`
- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.TodoPanel`
- `de.heckenmann.visualagent.ui.compose.FilesPanel`
- `de.heckenmann.visualagent.ui.compose.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.compose.SettingsPanel`
- `de.heckenmann.visualagent.ui.compose.CanvasPanel`

## Acceptance Criteria
- User action buttons in maintained Compose layouts do not define visible `text` labels.
- Every icon-only button has a tooltip that states the action.
- Canvas toolbar buttons are icon-only and describe their action through tooltips.
- The left navigation rail uses icon-only buttons with hover tooltips.
- Workspace panel headers include panel icons and icon-only actions for moving or hiding the panel.
- Dialog-provided standard buttons are allowed to keep text labels.
