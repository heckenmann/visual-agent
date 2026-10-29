# Project Asset Provenance

On 2026-10-29 the project owner explicitly confirmed that the logo, icons, and
startup backgrounds were AI-generated for this project and did not incorporate
unresolved third-party templates. This records the owner's statement for #411;
it is not a legal guarantee of copyrightability or third-party non-infringement.
The project distributes its own material under the repository MIT license.

| Canonical tracked asset | SHA-256 |
| --- | --- |
| `modules/ui/src/main/resources/icons/visual-agent.svg` | `29a1a1ed4dde355de4dc7f3955cdb6b85a5bcfb47b26a004257cdd54de31e984` |
| `modules/desktop/src/main/composeResources/files/startup_background_dark.png` | `5a3c4aea6778e266dc7af82fd613a9b8022f28f6d5edcf9bbc31ace3bdf7f5cf` |
| `modules/desktop/src/main/composeResources/files/startup_background_light.png` | `1899810630ff0892a3bdbd306ab78e8628d9699d77dcb6f1f4614bec3428969d` |

The SVG is the canonical icon source. `./gradlew generateBranding` rasterizes it
with Apache Batik at 16, 24, 32, 48, 64, 128, 256, 512, and 1024 pixels.
`buildSrc/.../GenerateBranding.kt` writes the UI and Compose PNGs and assembles ICO
and ICNS files from those generated PNG payloads. Native packaging and desktop/UI
resources consume those generated outputs rather than independently sourced icons.
The two background PNGs are independently generated light/dark variants stored in
Git LFS and copied into Compose resources without a new third-party image source.

New or replaced assets require renewed provenance review; this statement only
covers the exact tracked inputs identified above.
