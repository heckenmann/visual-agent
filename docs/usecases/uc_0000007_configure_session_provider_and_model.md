# UC-0000007: Configure Session Provider And Model

## Goal

Let the user select the active provider, model, and related model settings for the current session.

## Primary Actor

Desktop user.

## Preconditions

- Provider catalog data is available.
- The conversation panel is visible.

## Main Flow

1. The user opens the global **Providers and models** overlay with the settings icon in the Conversation panel title bar.
2. The UI shows enabled provider connections and models selectable for the selected connection.
3. The user stages a provider, model, favorite, or profile change without affecting active agent requests.
4. The user may refresh the remote model catalog to update the local selection choices.
5. The user configures model instruction, context, startup history, parallel agents, tool timeout, and queue behavior in the same local draft.
6. The user selects **Save changes** to persist the staged catalog, conversation settings, and active provider/model together, or **Reset changes** to reload the persisted state from SQLite.
7. The user may press **Esc** or select the title-bar close action to discard the local draft without saving.
8. Later model requests resolve against the saved provider/model.

## Result

Main-agent requests use the user-selected provider and model unless an agent-specific override applies.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.workspace.SplitPanelHeader`
- `de.heckenmann.visualagent.ui.conversation.openConversationProviderSettings`
- `de.heckenmann.visualagent.ui.settings.providerSettingsOverlay`
- `de.heckenmann.visualagent.ui.settings.ComposeProtocolSettingsSupport`
- `de.heckenmann.visualagent.config.AppConfig`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`
- `de.heckenmann.visualagent.agent.ConfiguredLLMProvider`

## Acceptance Criteria

- Provider selection uses enabled provider profiles rather than free-form text.
- Provider/model edits stay local until the user explicitly saves the global overlay.
- Conversation settings stay local until the user explicitly saves the same global overlay.
- Reset discards the local draft and rereads the persisted configuration; it does not restore factory defaults.
- The overlay occupies 80% of the application window height, keeps its title bar visible, and shows a vertical scrollbar when content exceeds the available space.
- Provider selection and its model selection appear together in one main-agent connection section.
- The overlay close action is in its title bar; Reset and Save remain right-aligned in a fixed footer.
- Pressing **Esc** or the title-bar close action discards unsaved local changes.
- Provider and model controls include information icons explaining their effects.
- Endpoint and credential configuration is available only through the separate provider-profile editor.
- API-key entry is available in the provider-profile editor and is masked by default.
- Model selection displays only catalog models that are selectable for the active provider.
- The model section has a clear empty state when the active provider has no recognized selectable models.
- Provider/model changes persist across restart.
- Standard providers mirror settings to their legacy `AppConfig` fields.
- Custom provider profiles remain catalog-backed.
- Saving the main-agent selection does not mutate a profile beyond explicitly staged profile edits.
- Credentials are not exposed to model context or logs.
- Streaming is used automatically whenever the active provider supports it.
