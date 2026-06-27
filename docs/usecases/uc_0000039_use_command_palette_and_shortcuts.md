# UC-0000039: Use Command Palette And Shortcuts

## Goal

Allow fast keyboard-driven navigation through panel shortcuts and command palette actions.

## Primary Actor

Desktop user.

## Preconditions

- The main window has focus.
- Keyboard shortcut wiring is initialized.

## Main Flow

1. The user presses a panel shortcut or command palette shortcut.
2. The main window resolves the target command.
3. For `Cmd/Ctrl+1..6`, the requested internal panel is made visible.
4. For `Cmd/Ctrl+K`, the internal command palette opens above the workspace.
5. The user filters commands by typing.
6. The user clicks a command or presses `Enter` to run the first matching command.
7. The user presses `Esc` or the close icon to dismiss the palette without running a command.

## Result

The user can navigate without relying only on mouse interactions.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceShortcuts`
- `de.heckenmann.visualagent.ui.compose.ComposeCommandPaletteHost`

## Acceptance Criteria

- `Cmd/Ctrl+1..6` opens the Conversation, Todos, Files, Subagents, Settings, and Canvas panels in that order.
- The panel shortcut mapping is stable and covered by unit tests.
- `Cmd/Ctrl+K` opens an internal command palette, not a native dialog.
- The command palette can filter commands and run a selected command.
- `Esc` closes the command palette without executing a command.
