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

1. The splash screen loads client-local server bookmarks from the operating-system user-config directory.
2. The built-in Local server is always shown as the only currently executable target.
3. The user can add, edit, or delete a remote Visual Agent server bookmark in the shared modal frame.
4. Bookmark changes are validated and atomically persisted without contacting a Visual Agent server.
5. Remote entries visibly state that connecting is not available yet.
6. The user explicitly selects Local.
7. Only then does the desktop start the local application server, connect its protocol client, and evaluate server-owned onboarding.

## Result

Server-location bootstrap data remains separate from LLM provider configuration, credentials, models, and server persistence. The client-local bootstrap file remains client-owned after connection; server-owned settings are read later through `ApplicationPort`, never through direct database access.

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
- Client and server storage roots are resolved independently; a remote client never receives or computes the server database path.
- The splash uses a decorative bundled light/dark background with centered
  aspect-preserving crop and a theme-derived contrast overlay. Status, errors,
  and server-selection controls remain readable and accessible.
- Before connecting, appearance follows the operating system because theme
  configuration belongs to the server database. No duplicate client theme
  preference or direct database access is introduced. Once settings are loaded,
  startup screens use the server-selected Light, Dark, or System mode.
- Missing or invalid images fall back to the theme background without stopping
  startup. Resource reads and image decoding run off the UI thread.

## Background Assets and Dependency Decision

Splash and onboarding share the same 880 x 600 dp initial window dimensions and
the same paired 1519 x 1035 PNG resources. The generated light asset was normalized
by cropping one extra pixel of height to match the dark asset. Both resources use
Git LFS through the repository's existing PNG rule.

Use the existing Compose resources and `Image` APIs, not an additional image
loader. Library research on klibs.io and the official
[Compose resource documentation](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html)
confirmed that packaged byte loading and decoding are already supported.
The resource paths and safe loader live in `ComposeStartupBackground.kt`;
foreground sizing is independent of the background's intrinsic dimensions.
