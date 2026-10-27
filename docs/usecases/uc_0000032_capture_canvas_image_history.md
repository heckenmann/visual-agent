# UC-0000032: Capture Canvas Image History

## Goal

Allow an enabled model to capture the current canvas as an immutable PNG image and store that image in conversation history.

## Primary Actor

Enabled sub-agent.

## Preconditions

- The canvas tool is enabled for the sub-agent.
- The canvas can be rendered to an image.

## Main Flow

1. The model calls the canvas capture action with a requested format.
2. The current toolkit-neutral canvas state is rendered to PNG image bytes.
3. The image is stored as an immutable history entry.
4. Later canvas edits do not modify the stored image.

## Result

The conversation contains a durable visual snapshot that can be persisted and reloaded.

## Tool Calls

- `canvas` with action `captureImage` stores an immutable canvas image in conversation history.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.canvas.CanvasTool`
- `de.heckenmann.visualagent.canvas.InMemoryCanvasService`
- `de.heckenmann.visualagent.canvas.CanvasPngRenderer`
- `de.heckenmann.visualagent.canvas.CanvasCaptureLimits`

## Acceptance Criteria

- PNG format is accepted in this version; unsupported formats return a clear tool failure.
- Captured image history entries survive restart.
- Later canvas mutations do not change existing history images.
- Captured images are rendered from toolkit-neutral canvas data instead of a JavaFX or Compose node snapshot.
- History image previews shrink to the available conversation row width instead of overflowing narrow workspace panels.
- Actual text glyphs and imported managed image pixels appear in captures. Missing,
  external, malformed or oversized image references fail explicitly, never as a
  successful placeholder rectangle.
- Captures start at canvas origin, clip negative coordinates, and retain the existing
  minimum 800 by 600 pixel area. Maximum dimensions are 4096 by 4096 with a combined
  limit of 4,194,304 pixels. Larger or non-finite extents fail before allocation; the
  editable document is unchanged. Move or resize figures into the capture area to retry.
- Images are read through the registered workspace service, limited to 8 MiB encoded
  and the same dimension/pixel budget before decoding. PNG, JPEG and GIF are supported;
  animated images capture their static frame, not an animation. Scaling preserves aspect ratio.

## Rendering Decision

Research on 2026-10-27 reviewed [Klibs](https://klibs.io/package/org.jetbrains.skiko/skiko)
and the maintained [JetBrains Skiko project](https://github.com/JetBrains/skiko).
Reuse the Skiko version already selected by Compose (0.150.2), rather than adding an
unrelated image library or implementing glyph rasterization and codecs ourselves.
Its Apache-2.0 bindings expose Skia's CPU raster surfaces, shaping, PNG encoding and
image decoding without importing AWT/Swing or creating a window. Platform-specific
native runtimes are now also explicit for the standalone server. Their upstream
Skia/native notices remain part of the exact-artifact release license audit (#410).
