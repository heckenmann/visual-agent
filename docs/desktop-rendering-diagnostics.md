# Desktop startup rendering diagnostics

Diagnostic events are emitted in the local launcher log. On Linux, start the same package from a terminal with output redirected to a file to retain messages from before the embedded server starts.

| Local event | Meaning |
| --- | --- |
| `Desktop startup server readiness: ready` | The server connection and startup state are available. |
| `window=SPLASH`, `window=ONBOARDING`, `window=MAIN` | The native startup window being observed. |
| `backend=… previousBackend=…` | Compose reports Skiko's current backend selection. Look for later changes because Skiko can report an attempted backend before initialization succeeds. |
| `render-error` | A Compose render callback failed, or Skiko exhausted its native rendering backend fallback. |
| `window-error` | Compose reported a window exception whose source could not be attributed to rendering. |
| `ui-unresponsive` | A task posted to the desktop UI dispatcher failed to run within five seconds. One task is kept pending during a stall. `ui-responsive` records recovery. |
| `visualIntegrity=unknown` | The observed window's presented pixels have yet to be checked. This includes launches that reach `VISUAL_AGENT_DESKTOP_READY`. |

The log writer and watchdog run off the UI thread. The watchdog ends when the splash or automatic onboarding window is replaced or closed. The first main window is observed for up to 15 seconds. Waiting for a server or for user input does not cause a timeout while the UI keeps responding. The Compose exception handler also receives event and background exceptions. Native graphics failures that never reach Compose and corrupted frames delivered without an exception can escape automatic diagnosis.

## Reproduce on the affected machine

Use the same release artifact, display, display scale, driver, compositor session, and startup action for each group. Close the application between runs. Start the normal build repeatedly, then repeat with software rendering set per launch. Set `launcher` to the executable installed by the package (listed by `dpkg-query -L visual-agent` on Debian) or the AppImage.

```bash
# Run multiple attempts per backend and keep one local log per attempt.
launcher="/path/to/Visual Agent"
env -u SKIKO_RENDER_API "$launcher" > visual-agent-default-01.log 2>&1
SKIKO_RENDER_API=SOFTWARE "$launcher" > visual-agent-software-01.log 2>&1
```

For each attempt, note the package format and version, distro, desktop session (`XDG_SESSION_TYPE`, `XDG_CURRENT_DESKTOP`), GPU and driver, display scale, reported backend, and whether the splash, onboarding, or main window showed displaced blocks. Record the number of failures per backend. Compare the observed backend logs even if a backend was explicitly requested. On Linux, `SOFTWARE` selects `SOFTWARE_FAST`, with `SOFTWARE_COMPAT` available as a fallback. If one group is affected, repeat with the test pattern to identify a concrete frame. If only software starts are clean, the software command above can be used for a one-shot launch while investigating.

## Check a user-authorized capture

1. Start the affected artifact with `VISUAL_AGENT_STARTUP_RENDER_PATTERN=1`. A 4 × 3 color pattern appears 12 dp from the bottom-left corner of the content area in each observed window. Leave the affected window on screen.
2. Use the desktop's interactive screenshot tool to capture the presented area. Save a local PNG cropped to the 96 × 72 dp pattern rectangle (scaled to the screenshot resolution). Exclude window borders, the 12 dp margin, and any overlapping windows. If the pattern itself is torn or missing, use its expected bounds from a known good launch on the same display and scale. Keep the original privately if needed to compare the pattern with the rest of the window.
3. Pass only the cropped image to the same launcher. For an executable JAR, put the same arguments after `java -jar <artifact>`.

```bash
"$launcher" --check-startup-render-pattern /path/to/marker-crop.png
```

Exit code 0 means that the sampled regions match. Exit code 1 means that one or more regions differ beyond tolerance. Confirm that the crop is correct and unobscured before attributing a mismatch to graphics corruption. Exit code 2 means unknown, including unreadable input, invalid crop dimensions, or supplying no capture. If capture is unavailable or denied, normal startup continues and visual integrity stays unknown.

The comparator samples the interior of each tile at normalized coordinates and tolerates modest RGB variation, so fractional scaling and small color differences are accepted. It detects displaced, missing, or recolored tiles in the sampled regions. The result covers the marker area only. An occluded or incorrectly cropped image can yield a mismatch, and corruption outside the samples or after the compositor capture can be missed. No screen capture or image transfer is performed by the launcher.

## Wayland and library evaluation

[XDG Screenshot portal](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Screenshot.html) requests an OS-managed capture and returns a URI through the asynchronous response. Version 2 adds an interactive hint and leaves selection behavior to the desktop. Version 3 can advertise and request a user-selected window or area. Query the affected desktop without requesting a capture:

```bash
gdbus call --session --dest org.freedesktop.portal.Desktop \
    --object-path /org/freedesktop/portal/desktop \
    --method org.freedesktop.DBus.Properties.Get org.freedesktop.portal.Screenshot version
```

The prototype uses the desktop screenshot tool for consent and cropping. The early startup path has no portal or D-Bus dependency. [dbus-java](https://github.com/hypfvieh/dbus-java) provides a maintained JVM transport if portal integration is needed after testing. Compose Desktop snapshot tooling such as [Roborazzi](https://github.com/takahirom/roborazzi#experimental-compose-desktop-preview-support) is useful for offscreen rendering tests, which cannot diagnose corruption in a real window after compositing. The prototype uses the existing Compose drawing and Skia image decoding APIs.
