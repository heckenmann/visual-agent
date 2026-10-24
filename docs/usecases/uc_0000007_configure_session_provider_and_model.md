# UC-0000007: Configure Session Provider And Model

## Goal

Choose the model for the next main-agent request directly beside the message input,
without opening provider administration or saving an unrelated settings form.

## Primary Actor

Desktop user; an explicitly permitted agent can change the same active selection.

## Preconditions

- The desktop is connected to a Visual Agent server.
- The Conversation panel is visible. Its header need not be visible.

## Main Flow

1. The composer displays the active model and its provider in a compact selector.
   It is present for both the scrolling and pinned composer placements.
2. Open the selector. Search model IDs, display names, or provider names. Favorites
   appear first; other entries are grouped by provider. Identical model IDs from
   different providers retain their distinct provider identities.
3. Select a model. Switching to another provider first refreshes that provider's
   catalog asynchronously. The server validates the selection and persists the
   provider/model pair without changing profile definitions or unrelated settings.
4. The configured context budget adopts a positive model-reported limit, whether
   larger or smaller. Missing catalog limits are resolved through provider metadata;
   when no limit can be established, retain the configured budget without guessing.
5. Subsequent requests use the new selection. Already running requests retain their
   captured provider/model; changing the selection never resends the input draft.
6. Refresh the current provider catalog or toggle a model favorite through icon-only
   actions. Refresh does not select a different model automatically.
7. Manage providers opens Settings > Providers. The Conversation header's settings
   action instead opens a small Model/Conversation dialog for context budget,
   interpolation, follow-up suggestions and queue behavior.

## Alternative And Error Flows

- Discovery or selection failure retains the last saved selection and shows an error.
- Explicitly unsupported CHAT entries are disabled with an explanation. Unknown
  capability declarations remain eligible; no model-name heuristic is used.
- Disabled or profile-filtered models do not become selectable.
- External or tool-driven selection changes refresh the displayed model without
  rewriting the user's message or resetting scroll/pinning behavior.
- A context-budget draft cannot be applied after its associated active model changes.
- No matching models produces an explicit empty state with provider administration
  still reachable.

## Tool Calls

- `model_selection` actions `get`, `listProviders`, `listModels`, `refreshModels` and
  `set` access the same server catalog and selection. Existing permissions apply.
- Account authorization remains an explicit user action, not a model tool call.

## Code Entry Points

- `ui/conversation/models/ConversationWorkspacePanel.kt`
- `ui/conversation/models/ConversationModelPicker.kt`
- `ui/conversation/models/ConversationModelState.kt`
- `ui/settings/forms/ConversationSettingsPage.kt`
- `SettingsPort.selectConversationModel`, `SettingsPort.saveSection`
- `SpringSettingsPort`, `SpringProviderPort`, `ProviderCatalogService`

## Acceptance Criteria

- Model selection is immediate; it does not require a separate settings save.
- Selecting a model does not overwrite instructions, appearance, credentials,
  provider defaults, favorites or other independently edited settings.
- Native provider schema/streaming behavior and request-scoped tool permissions remain unchanged.
- Known context limits are honored in both directions; unreported limits stay unknown.
- Model search, favorites, refresh and administration navigation work with keyboard
  and accessibility semantics, including narrow panels and theme changes.
- Queries and network operations run off the UI thread. Scrolling, recomposition
  and resizing do not initiate provider/database reads.
- The active selection and favorites survive restart. No credentials enter logs,
  model context or search metadata.

## Related Use Cases

- UC-0000008: provider administration.
- UC-0000153: separated global Settings destinations and draft ownership.
