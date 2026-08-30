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
3. The application shows a tooltip describing the button action and uses the hand cursor for enabled actions; a disabled action uses the platform default cursor.
4. The user clicks the button to execute that action.

## Alternative Flows
- If a button is disabled, it still remains icon-only and its tooltip explains the action when Compose Multiplatform displays disabled-node tooltips.
- Platform dialog buttons such as OK, Cancel, and Close may retain native text labels to preserve operating-system conventions.

## Tool Calls

- None.

## Code Entry Points
- `de.heckenmann.visualagent.ui.components.ActionIconButton`
- `de.heckenmann.visualagent.ui.components.ActionTooltip`
- `de.heckenmann.visualagent.ui.workspace.ComposeRail`
- `de.heckenmann.visualagent.ui.workspace.ComposeSplitWorkspace`
- `de.heckenmann.visualagent.ui.workspace.ScrollArrow`
- `de.heckenmann.visualagent.ui.workspace.PanelResizer`
- `de.heckenmann.visualagent.ui.conversation.ConversationPanel`
- `de.heckenmann.visualagent.ui.todo.TodoPanel`
- `de.heckenmann.visualagent.ui.files.FilesPanel`
- `de.heckenmann.visualagent.ui.application.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.settings.SettingsPanel`
- `de.heckenmann.visualagent.ui.canvas.CanvasPanel`
- `de.heckenmann.visualagent.ui.modal.composeModalHost`

## Notes
- The `PanelDragHandle` composable was removed; the entire panel header now acts as the draggable reorder handle, except for interactive buttons.

## Acceptance Criteria
- Compact workspace actions may be icon-only; buttons with visible labels also include a matching icon unless they are a documented standard-action exception.
- Every icon-only button has a tooltip that states the action.
- Icon-only actions override text-field descendants with a hand cursor while enabled, so an I-beam appears only over actual text-entry surfaces.
- Canvas toolbar buttons are icon-only and describe their action through tooltips.
- The left navigation rail uses icon-only buttons with hover tooltips.
- Workspace panel headers include panel icons and icon-only actions for moving or hiding the panel.
- Dialog-provided standard buttons are allowed to keep text labels.
