# UC-0000159: Configure Application Task Models

## Goal

Let a user choose which provider model Visual Agent uses for supported task capabilities across the application.

## Primary Actor

Desktop user.

## Preconditions

- At least one provider profile is configured.
- Models selected for a task have a declared matching capability.

## Main Flow

1. The user opens **Settings → Task Models**.
2. The application lists task-specific model defaults for Vision, Speech recognition, Speech output, Image generation, and Embeddings.
3. If no task model is configured, Visual Agent automatically assigns the first selectable model that declares the matching capability. Automatic Vision selection stays within the active provider. The user may explicitly choose a Vision model from another provider.
4. The user saves the task defaults independently of provider profile edits.
5. Vision, audio input, audio output, image generation, and embedding selections are stored independently as global task defaults.
6. A per-sub-agent model explicitly assigned for the same capability takes precedence. If no capable model is available, existing request and conversation selections continue to apply until discovery finds one.

## Result

Application-wide model defaults are stored with the provider catalog and survive restart. Choosing a task model does not change the active conversation model.

## Failure Cases

- A provider or model is missing, disabled, filtered, or does not support the selected capability: reject the save and retain the current catalog.
- The catalog changed after the settings draft was loaded: reject the stale save and request a reload.
- A model becomes unavailable after it was selected: preserve the stored choice for review, but do not route a request to a model that no longer supports its capability.

## Acceptance Criteria

- **Settings → Task Models** exposes independent Vision, Speech recognition, Speech output, Image generation, and Embeddings choices across enabled provider profiles.
- The model options include only models that declare the relevant capability.
- The first available capable model is assigned automatically when a task has no selection; later discovery does not replace a saved selection.
- If no capable model is available, existing request and conversation model behavior is preserved.
- Main-agent vision requests, workspace image analysis, and embeddings honor their application-wide defaults.
- Speech recognition, speech output, and image generation assignments can be configured independently and persist with the provider catalog.
- An explicit per-sub-agent capability model remains higher priority than the application default.
- Saving task model choices uses a database-conditional catalog mutation.
- Existing catalog JSON without task assignments decodes with no application-wide defaults.

## Tool Calls

- `workspace_file` image analysis uses the configured Vision model when no explicit sub-agent assignment overrides it.

## Code Entry Points

- `TaskModelSettingsPanel`, `ProviderCatalogService`, `ConfiguredLLMProvider`

## Implementation Decision

Task assignments extend the existing versioned provider-catalog preference and compare-and-set mutation. Automatic selection prefers the active provider, then provider identifiers; each provider's default model is preferred before model identifiers. The global task model settings live in their own Settings destination, separate from provider profile editing. Speech execution is tracked in [#483](https://github.com/heckenmann/visual-agent/issues/483) and image generation execution in [#231](https://github.com/heckenmann/visual-agent/issues/231).
