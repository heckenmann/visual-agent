UX Task List
===============

Status: The tasks below have been seeded into the in-memory TodoManager by AgentManager.seedUxTodos(). Autonomous processing is NOT started automatically; call AgentManager.startAutonomousProcessing(seed = true) to begin processing. Do NOT run the application repeatedly during development — start processing only when you're ready.

Tasks (seeded)
1. ChatPanel: implement message grouping visual polish
2. ChatPanel: implement typing indicator & streaming partial responses
3. ChatPanel: add message actions (retry, edit, delete, pin, reactions)
4. ChatPanel: virtualize message list for large histories
5. ChatPanel: accessibility pass (focus, labels, contrast)
6. MainWindow: persistent left navigation with active state and tooltips
7. [Done] MainWindow: command palette (Cmd/Ctrl+K) to switch panels/search
8. [Done] StatusBar: actionable controls (Retry, Reconnect)
9. SessionPanel: model search, filter, favorites and quick actions
10. SessionPanel: improve loading/error states for model info
11. TodoPanel: inline create/edit without modal
12. TodoPanel: undo snackbar for deletes
13. TodoPanel: bulk actions and filters
14. SubAgentsPanel: agent cards with quick run/stop and logs preview
15. SubAgentsPanel: agent creation wizard with templates
16. CanvasPanel: toolbar (pen, eraser, undo/redo, export)
17. CanvasPanel: pan/zoom, grid, snapshots saving
18. ApplicationSettings: group settings, live theme preview, import/export config
19. Cross-cutting: component library mapping and CSS tokenization
20. Cross-cutting: accessibility pass and contrast audit

How to start processing (manual)
- From the running application (developer): open a debug console and call
  `agentManager.startAutonomousProcessing(seed = true)` once.
- From code: call the same method at a controlled place (not in MainWindow init).

Notes
- The TodoManager is in-memory. If you want persistence across restarts, persist todos to KnowledgeDb or the SQLite DB.
- Avoid repeated application restarts while autonomous processing is running — it may cause resource contention and rate limits against local models.
