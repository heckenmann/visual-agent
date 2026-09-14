# UC-0000113: Select a Visual Agent server at startup

## Goal

Allow the desktop client to show the built-in local Visual Agent server and saved
remote Visual Agent server bookmarks before any application server is contacted.

## Primary Actors

- User
- Desktop startup host

## Preconditions

- The desktop application is starting.

## Main Flow

1. The splash screen loads client-local server bookmarks from the operating-system user-data directory.
2. The built-in Local server is always shown as the only currently executable target.
3. The user can add, edit, or delete a remote Visual Agent server bookmark in the shared modal frame.
4. Bookmark changes are validated and atomically persisted without contacting a Visual Agent server.
5. Remote entries visibly state that connecting is not available yet.
6. The user explicitly selects Local.
7. Only then does the desktop start the local application server, connect its protocol client, and evaluate server-owned onboarding.

## Result

Server-location bootstrap data remains separate from LLM provider configuration, credentials, models, and server persistence.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.desktop.DesktopServerBookmarkStore`
- `de.heckenmann.visualagent.desktop.ComposeStartupServerBookmarks`
- `de.heckenmann.visualagent.desktop.ComposeStartupHost`

## Acceptance Criteria

- Local is available when the bookmark file is missing or invalid.
- Bookmark JSON contains only Visual Agent server locations and no secrets.
- Remote server bookmarks cannot start an unsupported remote transport.
- Rendering the splash or loading bookmarks never starts a local server.
- Selecting Local starts the bundled server only after the selection action.
