# UC-0000153: Navigate Settings Destinations

## Goal

Keep global setup out of everyday Conversation interaction, with stable Settings
destinations instead of one overloaded provider/conversation form.

## Actors

- Desktop user.
- Connected Visual Agent server owning provider definitions and execution preferences.

## Main Flow

1. Open the existing Settings workspace panel.
2. Choose Appearance, Providers, Agent or Updates in the tab navigation.
3. Appearance contains theme, font size, UI scale and panel-label preferences.
4. Providers contains profile creation/edit/removal, authentication, staged model
   discovery, capability overrides and advanced provider/model options. Inspecting
   a profile/model does not change the main agent's active selection.
5. Agent contains durable instructions, the revisioned memory document, startup
   history, parallelism, tool timeout and memory size limits. Memory is saved as
   its own revision-checked resource, independently of the surrounding form.
6. Updates contains the existing version/update preferences and actions.
7. Switch destinations without losing unsaved settings or memory drafts. Each
   category has its own Save/Reset actions; these do not submit another category.
8. The model picker can reveal Providers directly, opening the Settings panel if hidden.
9. The Conversation settings dialog contains only Model and Conversation tabs.
   Interpolation details are expandable and automatic-mode details remain visible
   while enabled. Follow-up count and idle delay appear only while suggestions are enabled.

## Persistence And Failure Behavior

- Section saves write only their owned preference keys. They never rewrite credentials,
  the provider catalog or a copied active selection from another form.
- Provider-profile saves compare their baseline with database state, ignoring list
  presentation order and retaining authoritative OAuth registrations. Conflicts
  retain local edits and require reload rather than silently erasing concurrent work.
- The active provider must be switched in Conversation before it can be removed or
  disabled through provider administration.
- Invalid/incomplete advanced options remain visible in the draft and block Save.
- Save failures retain the draft and show a concise error. Reset rereads server state,
  not factory defaults.
- Server I/O is asynchronous from presentation. Navigation/recomposition itself does
  not execute blocking provider, filesystem or database work on the UI thread.

## Tool Calls

- `model_selection` continues to expose model/catalog operations under existing permissions.
- Existing `memory` tool operations use the same revisioned document shown under Agent.
- Settings navigation is presentation-only; it does not create a new model-callable
  authentication operation or widen any tool allowlist.

## Code Entry Points

- `ui/settings/navigation/SettingsWorkspacePanel.kt`, `SettingsNavigationState.kt`
- `ui/settings/forms/ScopedSettingsForm.kt`, `ConversationSettingsPage.kt`
- `ui/settings/providers/ProviderManagementPanel.kt`, `ProviderModelCatalog.kt`
- `ui/settings/agent/AgentSettingsPage.kt`, `MainAgentMemoryEditor.kt`
- `SettingsPort.saveSection`, `ProviderPort.replaceProfiles`
- `server/settings/SettingsSectionUpdates.kt`, `ProviderCatalogService.replaceProfiles`

## Library Decision

Use the existing Compose Multiplatform Material 3 tab, menu, text-input and layout
components. Klibs and the JetBrains/AndroidX sources were checked before implementation;
these maintained Apache-2.0 components already cover the interaction. No new navigation
or UI dependency and no framework upgrade are needed.

- https://klibs.io/package/androidx.compose.material3/material3/1.5.0-beta02
- https://github.com/JetBrains/compose-multiplatform
- https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html

## Verification

- Destination separation, external navigation and retained drafts.
- Section-scoped persistence preserving unrelated server values and credentials.
- Conditional interpolation/suggestion controls.
- Revisioned memory saving and conflict behavior.
- Model selection/search/favorites and both composer placements (UC-0000007).
- Existing profile/authentication and theme tests remain required.

## Related Issues

- #494: this restructuring.
- #368: future searchable settings navigation; full settings search is not introduced here.
