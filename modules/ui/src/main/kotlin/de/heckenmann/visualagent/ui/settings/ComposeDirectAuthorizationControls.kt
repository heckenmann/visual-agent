package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import de.heckenmann.visualagent.protocol.DirectProviderAuthMode
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit browser sign-in actions; networking, token ownership, and callbacks remain server-side. */
@Composable
internal fun DirectAuthorizationControls(
    profile: ProviderProfile,
    port: ProviderPort,
    onReferenceChanged: (String, String?) -> Unit,
) {
    if (profile.adapter != ProviderAdapter.CODEX_DIRECT || profile.directAuthMode != DirectProviderAuthMode.CHATGPT_OAUTH) return
    val scope = rememberCoroutineScope()
    val browser = LocalUriHandler.current
    val publishReference by rememberUpdatedState(onReferenceChanged)
    val hasRegistration = profile.credentialReference != null
    var operation by remember(profile.id) { mutableStateOf<Job?>(null) }
    var status by remember(profile.id) { mutableStateOf<String?>(null) }
    DisposableEffect(profile.id) { onDispose { operation?.cancel() } }
    Row {
        ActionIconButton(
            Icons.Filled.AccountCircle,
            if (hasRegistration) "Sign in to ChatGPT again" else "Continue with ChatGPT",
            enabled =
                operation == null,
            onClick = {
                operation =
                    scope.launch {
                        var sessionId: String? = null
                        try {
                            status = "Complete ChatGPT authorization in your browser."
                            val session = withContext(Dispatchers.IO) { port.beginDirectSignIn(profile) }
                            sessionId = session.id
                            withContext(Dispatchers.IO) { browser.openUri(session.authorizationUrl) }
                            val result = withContext(Dispatchers.IO) { port.awaitDirectSignIn(session.id) }
                            publishReference(profile.id, result.credentialReference)
                            status = "${result.accountLabel} authorized. This account selection is saved."
                        } catch (cancelled: CancellationException) {
                            status = "ChatGPT authorization cancelled."
                            throw cancelled
                        } catch (_: Exception) {
                            status = "ChatGPT authorization failed. Retry sign-in and check plan permissions."
                        } finally {
                            sessionId?.let { id ->
                                withContext(NonCancellable + Dispatchers.IO) { runCatching { port.cancelDirectSignIn(id) } }
                            }
                            operation = null
                        }
                    }
            },
        )
        ActionIconButton(Icons.Filled.Close, "Cancel ChatGPT authorization", enabled = operation != null, onClick = { operation?.cancel() })
        ActionIconButton(
            Icons.AutoMirrored.Filled.Logout,
            "Sign out of ChatGPT",
            enabled = operation == null && profile.credentialReference != null,
            onClick = {
                operation =
                    scope.launch {
                        try {
                            val confirmed = withContext(Dispatchers.IO) { port.signOutDirectProvider(profile) }
                            publishReference(profile.id, null)
                            status =
                                if (confirmed) {
                                    "Signed out of ChatGPT."
                                } else {
                                    "Signed out locally. Remote revocation was not confirmed; disconnect the app in ChatGPT Settings if needed."
                                }
                        } catch (_: Exception) {
                            status = "Could not finish sign-out. Retry before using this account."
                        } finally {
                            operation = null
                        }
                    }
            },
        )
    }
    PanelInfoBox(
        status ?: if (hasRegistration) {
            "ChatGPT sign-in is configured for this provider. Sign out first to use a different account."
        } else {
            "ChatGPT authorization is required."
        },
    )
}
