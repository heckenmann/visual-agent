# UC-0000064: Manage Model Favorites And Filters

## Goal

Let the user mark favorite models and filter the visible model list by search text or favorites-only mode.

## Primary Actor

Desktop user.

## Preconditions

- Compose settings panel is visible.
- Model list has been loaded or restored from catalog data.

## Main Flow

1. The user searches the model list or enables favorites-only mode.
2. The settings panel filters selectable models.
3. The user toggles favorite status for the selected model.
4. Favorite IDs are persisted.
5. The favorite button updates to reflect current state.

## Result

Large model catalogs are easier to navigate.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.SettingsPanel`
- `de.heckenmann.visualagent.ui.compose.ActionIconButton`
- `de.heckenmann.visualagent.config.AppConfig.favoriteModels`

## Acceptance Criteria

- Favorite changes persist across restart.
- Favorites-only mode uses the persisted favorite model IDs.
- Current selected model is preserved when still visible.
