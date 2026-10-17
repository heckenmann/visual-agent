# Visual Agent logo

The application uses the selected **F2 Facette** mark: a closed eye with a
stronger upper lid and a four-point star with a diagonally detached right tip.
The separated facet suggests an AI impulse becoming an independent visual
object. The artwork contains no letters, gradients, shadows, or embedded images.

## Source and color

The single editable source is
`modules/ui/src/main/resources/icons/visual-agent.svg` with a 512 × 512 viewBox.
The application-icon treatment uses Material3 dark primary **#D0BCFF** on dark
surface **#141218**, from the baseline `darkColorScheme()` used by
`ComposeWorkspaceTheme`. The dark tile preserves contrast when the same
application icon appears on either light or dark UI and operating-system
backgrounds. Transparent outer corners preserve the rounded tile.

Keep the mark's proportions fixed. For freestanding uses, leave at least the
height of the detached facet (approximately 26 viewBox units) clear around the
eye; the app tile already includes a 48-unit horizontal inset.

The diagonal gap becomes less distinct below 32 pixels. The small ICO sizes
retain the complete selected F2 geometry, without claiming that the detached
facet remains separately recognizable at 16 pixels. No trademark clearance
is implied by adoption of this design.

## Regenerate derived icons

Install Inkscape and Python 3 with Pillow, then run:

```bash
python3 scripts/generate-app-icons.py
```

This updates the existing resources automatically consumed by the application:

| Asset | Existing consumer |
| --- | --- |
| `modules/ui/src/main/resources/icons/visual-agent.png` | Workspace header, `AppIdentity`, Linux packaging and development launcher |
| `modules/desktop/src/main/composeResources/drawable/visual_agent.png` | Splash and native Compose window icons |
| `modules/desktop/packaging/icons/visual-agent.ico` | Windows installer icon, sizes 16, 24, 32, 48, 64, 128, 256 |
| `modules/desktop/packaging/icons/visual-agent.icns` | macOS bundle icon |

The README uses the source SVG directly. No resource-path or application-code
changes are needed. Both PNG copies are byte-identical exports of the source;
ICO and ICNS contain size-specific raster representations of the same artwork.

The SVG is ordinary Git text. All derived PNG, ICO, and ICNS files are stored
through Git LFS; checkouts must download LFS objects before building. Existing
CI checkout steps already enable `lfs: true`. Replacing the assets removes the
old artwork from the current tree without rewriting repository history.
