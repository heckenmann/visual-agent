# UC-0000034: Arrange Internal Windows

## Goal

Let users arrange application panels as draggable internal windows inside the main workspace.

## Primary Actor

Desktop user.

## Preconditions

- The main window workspace is initialized.
- Panels are registered with the workspace window manager.

## Main Flow

1. The user opens a panel from navigation.
2. The panel appears as an internal workspace window.
3. The user drags or resizes the window.
4. During dragging, the window moves smoothly without forcing full panel layout on every pointer event.
5. Large panel contents remain scrollable without expensive desktop chrome effects being recalculated.
6. Window manager commits final bounds and z-order when the drag completes.

## Result

The user can build a custom workspace layout inside the application.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.InternalWorkspaceWindow`
- `de.heckenmann.visualagent.ui.WorkspaceWindowManager`
- `de.heckenmann.visualagent.ui.MainWindowWorkspace`

## Acceptance Criteria

- Internal windows stay inside usable workspace bounds.
- Each registered panel can be opened through navigation.
- Dragging remains responsive for windows containing heavier panels such as canvas or file tables.
- Workspace window chrome avoids JavaFX `-fx-effect` styling so scrolling large panels does not repaint expensive shadows.
