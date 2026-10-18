# Visual Agent logo

The application uses the selected **F2 Facette** mark: a closed eye with a
stronger upper lid and a four-point star with a diagonally detached right tip.
The artwork contains no letters, gradients, shadows, or embedded images.

## Canonical SVG

The single editable source is
`modules/ui/src/main/resources/icons/visual-agent.svg` with a 512 × 512 viewBox.
The icon uses Material3 dark primary **#D0BCFF** over dark surface **#141218**.
The same dark tile works with light/dark UI themes and OS backgrounds.

## Build-generated icon assets

```bash
./gradlew generateBranding
./gradlew verifyGeneratedBranding
```

All application icons are automatically generated from the SVG during the normal
Gradle resource-processing, packaging and verification tasks. No manual
Inkscape, Python, Pillow or other native imaging tools are required. The
build-only Apache Batik renderer generates PNGs on the JVM. PNGs are then packed
into standard Windows ICO and macOS ICNS containers.

Files are written below `build/generated/branding/` at the repository root:

| Generated path | Consumers |
| --- | --- |
| `ui/icons/visual-agent.png` | Workspace header, `AppIdentity`, Linux launcher and AppImage |
| `compose/drawable/visual_agent.png` | Splash and Compose native window icons |
| `packaging/visual-agent.ico` | Windows EXE/MSI icon, 16, 24, 32, 48, 64, 128, 256 px |
| `packaging/visual-agent.icns` | macOS bundle and Dock icon, 16 to 1024 px |

Both PNGs are byte-identical 1024 × 1024 exports. The Gradle task declares
tracked SVG inputs and output directories for incremental builds and caching.
The root `check` task validates image signatures, dimensions and consistency,
while the desktop packaging verification checks native icon signatures.

The README references the SVG directly. The only application icon committed
to Git is the SVG; generated raster and native icon assets are neither checked
in nor stored in Git LFS. The existing LFS settings remain for unrelated binary
assets. Native installers are built on their respective CI operating systems.

The small icon sizes retain the complete geometry but the detached facet is
less legible below 32 px. No trademark clearance is implied.
