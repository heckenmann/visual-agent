# UC-0000095: Toggle navigation rail panel labels

## Goal

Users can choose whether the left navigation rail displays panel names beside panel icons.

## Preconditions

- The Visual Agent desktop application is running.
- The left navigation rail is visible.

## Main Flow

1. The user clicks the panel-label toggle button in the navigation rail.
2. The application immediately switches between compact icon-only buttons and buttons showing each panel name.
3. In labelled mode, the rail grows to fit the widest rendered panel name without clipping.
4. The application persists the selected mode.
5. On the next launch, the application restores the persisted mode.

## Alternative Flows

- If the user clicks the toggle again, the application returns to compact icon-only mode.
- Hidden workspace panels retain their normal rail entry and are affected by the same display mode.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.workspace.ComposeRail`
- `de.heckenmann.visualagent.ui.workspace.DraggableRailButton`
- `de.heckenmann.visualagent.config.AppConfigBean.showPanelLabels`
- `de.heckenmann.visualagent.config.AppConfigPersistenceBinder`

## Acceptance Criteria

- The labelled mode is the default until the user chooses the compact icon-only mode.
- The rail toggle changes the mode immediately.
- Labelled buttons use the full rail width and show their complete panel names.
- The preference survives application restart.
