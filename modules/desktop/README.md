# Desktop Module

`:desktop` is the Compose Desktop launcher and packaging module. It shows the splash screen,
restores the desktop window, and either connects to a configured remote server or starts the
local `:application` server in the same JVM.

## Responsibilities

- Provides `de.heckenmann.visualagent.desktop.DesktopMain`, the desktop entry point.
- Owns server-endpoint selection, protocol handshake, startup/shutdown coordination, and the
  desktop-only image port for local client files.
- Packages native distributions for Linux, Windows, and macOS, plus the Linux AppImage.

It depends on `:ui`, `:protocol`, and `:application`. It must not duplicate Spring adapters,
provider access, persistence, or workspace access from `:application`.

## Commands

Run from the repository root:

```bash
./gradlew :desktop:run
./gradlew :desktop:test
./gradlew :desktop:createDistributable
```

