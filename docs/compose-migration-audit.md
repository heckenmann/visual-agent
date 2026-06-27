# Compose Multiplatform Migration Audit

## Summary

Issue 48 evaluated whether Visual Agent can move from JavaFX to Compose Multiplatform. The local branch now implements the production desktop runtime with Compose Multiplatform and removes the former JavaFX/FXML/JHotDraw UI path from the source tree.

Decision: proceed with Compose Multiplatform for the desktop runtime.

## Scope Boundary

The migration replaces the Visual Agent desktop UI toolkit. It does not implement the future embedded browser feature. Browser and web-search backend work remains intentionally tracked outside this migration in GitHub issues #16 and #40.

## Current Evidence

- `Main.kt` starts the Compose desktop application.
- `build.gradle.kts` uses Compose Multiplatform dependencies and no OpenJFX, AtlantaFX, Ikonli JavaFX, or JHotDraw dependencies.
- `desktopApiUsageCheck` is part of `check` and rejects legacy desktop image/toolkit API usage below `src/main` and `src/test`.
- `./gradlew ktlintCheck --no-daemon`, `./gradlew check --no-daemon`, and `./gradlew build --no-daemon` pass locally.
- `./gradlew run --no-daemon` starts the Spring context and Compose desktop runtime locally.

## Requirement Audit

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Full JavaFX removal via Compose is feasible | Complete | Production entry point uses Compose; legacy JavaFX/FXML sources and resources are removed from the build path; `desktopApiUsageCheck` is enabled. |
| Compare Compose with Swing/FlatLaf path from issue 46 | Complete | Issue 48 documents Compose vs Swing/FlatLaf tradeoffs and recommends Compose for the Kotlin-first UI. |
| No JavaFX dependencies remain | Complete | `build.gradle.kts` contains Compose dependencies and no JavaFX, AtlantaFX, Ikonli JavaFX, or JHotDraw dependencies. |
| Semantic workspace panels | Complete | `ComposeSplitWorkspace`, `splitWorkspaceBounds`, and `workspace/layout` services provide user-ordered stage, inspector, and deck panel slots without overlap or pointer-driven resize states. |
| Large panels scroll without old chrome repaint costs | Complete | Compose panels use Compose scroll containers and cheap window chrome; previous JavaFX CSS/shadow effects are removed. |
| Theme and visual language | Complete | `ComposeWorkspaceTheme` provides Dracula-style Compose theme tokens and runtime settings remain DB-backed. |
| Canvas replacement | Complete | `CanvasOperations`, `InMemoryCanvasService`, `CanvasDocumentCodec`, `CanvasPngRenderer`, and `ComposeCanvasPanel` provide editable figures, selection, move, resize, delete, PNG capture, persistence, workspace save/open, and tool calls. |
| Workspace files and canvas objects open/save through UI and tools | Complete | Files panel imports/syncs/renames/deletes files and opens canvas documents; `canvas.saveDocument`, `canvas.openDocument`, and `workspace:file` actions cover tool-call paths. |
| Model/tool APIs can query/manipulate layout and canvas | Complete | `workspace:layout` and `canvas` tools remain toolkit-neutral and are tested. |
| Keyboard navigation and command palette | Complete | `Cmd/Ctrl+1..6` opens panels; `Cmd/Ctrl+K` opens the internal command palette; `Esc` closes it. |
| Internal modal behavior | Complete | `ComposeModalHost` provides internal confirmation modals for destructive UI actions. |
| File picker | Complete | Files panel uses FileKit's Compose file picker and keeps typed-path import as fallback. |
| Markdown rendering | Complete | Conversation messages are rendered through CommonMark-backed Compose rendering without pre-normalizing parser input. |
| PDF/image processing without desktop image APIs | Complete | PDF page previews and canvas/image metadata paths use toolkit-neutral renderers/header readers. |
| Automated tests remain feasible | Complete | Compose migration uses unit tests for models, tools, parsers, layout, workspace files, image readers, and canvas service/tool behavior. |
| Native packaging remains compatible | Complete | Compose native distribution configuration remains in `build.gradle.kts`; packaging-specific release automation is tracked separately. |
| Browser feasibility is analyzed separately | Complete | Compose core does not provide an embedded WebView in this migration; browser implementation remains in issues #16 and #40. |

## Runtime Note

Compose Desktop itself uses the JVM `java.desktop` stack internally. Visual Agent source code does not use AWT/Swing APIs, but Gradle run/native application tasks set `java.awt.headless=false` so Compose can discover screen density and start in desktop mode.

## Remaining Non-Blocking Work

- Full browser and web-search backend implementation: issues #16 and #40.
- Packaging/release automation for native installers: tracked separately.
- Additional GUI integration/screenshot coverage can be added after the migration branch stabilizes.

## Final Recommendation

Use Compose Multiplatform as the Visual Agent desktop UI toolkit. The branch demonstrates that the main window, semantic workspace panels, file import, canvas editing/persistence, internal modals, command palette, and model-facing tool contracts can run without the former JavaFX/JHotDraw UI stack.
