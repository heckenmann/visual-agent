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
3. The requested panel or command is activated.

## Result

The user can navigate without relying only on mouse interactions.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.MainWindow`
- `de.heckenmann.visualagent.ui.MainWindowNavigation`

## Acceptance Criteria

- `Cmd/Ctrl+1..6` can switch panels.
- `Cmd/Ctrl+K` opens command palette behavior where implemented.
