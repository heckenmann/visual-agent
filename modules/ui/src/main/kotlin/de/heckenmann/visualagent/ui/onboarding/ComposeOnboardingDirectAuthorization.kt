package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import de.heckenmann.visualagent.protocol.DirectProviderAuthMode
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.ui.settings.DirectAuthorizationControls

/** Reuses the settings authentication flow without losing draft edits made during sign-in. */
@Composable
internal fun OnboardingDirectAuthorization(
    draft: OnboardingProviderDraft,
    providers: ProviderPort?,
    onChange: (OnboardingProviderDraft) -> Unit,
) {
    val current by rememberUpdatedState(draft)
    val publish by rememberUpdatedState(onChange)
    providers?.let { port ->
        DirectAuthorizationControls(
            ProviderProfile(
                draft.id,
                draft.name,
                draft.adapter,
                draft.baseUrl,
                directAuthMode = draft.directAuthMode,
                credentialReference = draft.credentialReference,
            ),
            port,
        ) { id, reference ->
            if (current.id == id && current.directAuthMode == DirectProviderAuthMode.CHATGPT_OAUTH) {
                publish(current.copy(credentialReference = reference))
            }
        }
    }
}
