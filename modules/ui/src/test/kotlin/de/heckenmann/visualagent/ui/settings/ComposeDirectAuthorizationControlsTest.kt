package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import de.heckenmann.visualagent.protocol.DirectProviderAuthMode
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.ProviderSignInResult
import de.heckenmann.visualagent.protocol.ProviderSignInSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

/** Exercises authorization buttons without opening browsers, using accounts, or sending inference. */
@OptIn(ExperimentalTestApi::class)
class ComposeDirectAuthorizationControlsTest {
    private val port =
        mockk<ProviderPort> {
            every { addChangeListener(any()) } returns AutoCloseable { }
        }
    private val opened = CompletableDeferred<String>()
    private val changed = CompletableDeferred<Pair<String, String?>>()

    @Test
    fun `saved browser registration displays configured sign in instead of asking to log in again`() =
        runComposeUiTest {
            render(profile().copy(credentialReference = "saved-reference"))
            onNodeWithContentDescription("Continue with ChatGPT").assertIsNotEnabled()
            onNodeWithContentDescription("Sign out of ChatGPT").assertIsEnabled()
            onNodeWithText("ChatGPT sign-in is configured for this provider. Sign out first to use a different account.").assertExists()
            coVerify(exactly = 0) { port.beginDirectSignIn(any()) }
            coVerify(exactly = 0) { port.discoverModels(any()) }
        }

    @Test
    fun `completed browser sign in updates actions immediately without reopening settings`() =
        runComposeUiTest {
            var current by mutableStateOf(profile())
            val published = CompletableDeferred<Unit>()
            coEvery { port.beginDirectSignIn(any()) } returns ProviderSignInSession("attempt", "https://synthetic.invalid/authorize")
            coEvery { port.awaitDirectSignIn("attempt") } returns ProviderSignInResult("reference", "Synthetic account")
            coEvery { port.cancelDirectSignIn("attempt") } returns Unit
            renderProfile({ current }) { _, reference ->
                current = current.copy(credentialReference = reference)
                published.complete(Unit)
            }
            onNodeWithContentDescription("Continue with ChatGPT").performClick()
            awaitIdle()
            published.await()
            awaitIdle()
            onNodeWithContentDescription("Continue with ChatGPT").assertIsNotEnabled()
            onNodeWithContentDescription("Sign out of ChatGPT").assertIsEnabled()
            onNodeWithText("Synthetic account authorized. This account selection is saved.").assertExists()
        }

    @Test
    fun `stored registration replaces a stale unsigned settings draft without losing edits`() =
        runComposeUiTest {
            var current by mutableStateOf(profile().copy(name = "Unsaved name", options = mapOf("reasoningEffort" to "high")))
            renderProfile({ current }, storedProfile = { profile().copy(credentialReference = "saved-reference") }) { id, reference ->
                current = current.copy(credentialReference = reference)
                changed.complete(id to reference)
            }
            assertEquals("profile" to "saved-reference", changed.await())
            onNodeWithContentDescription("Continue with ChatGPT").assertIsNotEnabled()
            onNodeWithContentDescription("Sign out of ChatGPT").assertIsEnabled()
            assertEquals("Unsaved name", current.name)
            assertEquals(mapOf("reasoningEffort" to "high"), current.options)
            coVerify(exactly = 0) { port.beginDirectSignIn(any()) }
        }

    @Test
    fun `catalog events update login and logout actions while settings remain open`() =
        runComposeUiTest {
            var current by mutableStateOf(profile())
            val stored = AtomicReference(profile())
            var notify: () -> Unit = {}
            every { port.addChangeListener(any()) } answers {
                notify = firstArg()
                AutoCloseable { }
            }
            renderProfile({ current }, storedProfile = stored::get) { _, reference ->
                current = current.copy(credentialReference = reference)
            }
            onNodeWithContentDescription("Continue with ChatGPT").assertIsEnabled()
            onNodeWithContentDescription("Sign out of ChatGPT").assertIsNotEnabled()

            stored.set(profile().copy(credentialReference = "external-registration"))
            runOnIdle { notify() }
            waitUntil { current.credentialReference == "external-registration" }
            awaitIdle()
            onNodeWithContentDescription("Continue with ChatGPT").assertIsNotEnabled()
            onNodeWithContentDescription("Sign out of ChatGPT").assertIsEnabled()

            stored.set(profile())
            runOnIdle { notify() }
            waitUntil { current.credentialReference == null }
            awaitIdle()
            onNodeWithContentDescription("Continue with ChatGPT").assertIsEnabled()
            onNodeWithContentDescription("Sign out of ChatGPT").assertIsNotEnabled()
            coVerify(exactly = 0) { port.beginDirectSignIn(any()) }
            coVerify(exactly = 0) { port.signOutDirectProvider(any()) }
            coVerify(exactly = 0) { port.discoverModels(any()) }
        }

    @Test
    fun `explicit sign in opens only the returned URL and publishes a credential reference`() =
        runComposeUiTest {
            coEvery { port.beginDirectSignIn(any()) } returns ProviderSignInSession("attempt", "https://synthetic.invalid/authorize")
            coEvery { port.awaitDirectSignIn("attempt") } returns ProviderSignInResult("reference", "Synthetic account")
            coEvery { port.cancelDirectSignIn("attempt") } returns Unit
            render(profile())

            onNodeWithContentDescription("Continue with ChatGPT").assertIsEnabled()
            onNodeWithContentDescription("Sign out of ChatGPT").assertIsNotEnabled()

            onNodeWithContentDescription("Continue with ChatGPT").performClick()
            awaitIdle()

            assertEquals("profile" to "reference", changed.await())
            assertEquals("https://synthetic.invalid/authorize", opened.await())
            onNodeWithText("Synthetic account authorized. This account selection is saved.").assertExists()
            coVerify(exactly = 1) { port.beginDirectSignIn(any()) }
            coVerify(exactly = 0) { port.discoverModels(any()) }
        }

    @Test
    fun `cancel button cancels the server attempt while awaiting authorization`() =
        runComposeUiTest {
            val awaiting = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            coEvery { port.beginDirectSignIn(any()) } returns ProviderSignInSession("attempt", "https://synthetic.invalid/authorize")
            coEvery { port.awaitDirectSignIn("attempt") } coAnswers {
                awaiting.complete(Unit)
                awaitCancellation()
            }
            coEvery { port.cancelDirectSignIn("attempt") } coAnswers {
                cancelled.complete(Unit)
            }
            render(profile())
            onNodeWithContentDescription("Continue with ChatGPT").performClick()
            awaitIdle()
            awaiting.await()

            onNodeWithContentDescription("Continue with ChatGPT").assertIsNotEnabled()
            onNodeWithContentDescription("Sign out of ChatGPT").assertIsNotEnabled()
            onNodeWithContentDescription("Cancel ChatGPT authorization").assertIsEnabled()

            onNodeWithContentDescription("Cancel ChatGPT authorization").performClick()
            awaitIdle()

            cancelled.await()
            coVerify(exactly = 1) { port.cancelDirectSignIn("attempt") }
            assertEquals(false, changed.isCompleted)
        }

    @Test
    fun `sign out clears the selected reference and discloses unconfirmed remote revocation`() =
        runComposeUiTest {
            coEvery { port.signOutDirectProvider(any()) } returns false
            render(profile().copy(credentialReference = "reference"))

            onNodeWithContentDescription("Sign out of ChatGPT").performClick()
            awaitIdle()

            assertEquals("profile" to null, changed.await())
            onNodeWithText(
                "Signed out locally. Remote revocation was not confirmed; disconnect the app in ChatGPT Settings if needed.",
            ).assertExists()
            assertEquals(false, opened.isCompleted)
            coVerify(exactly = 1) { port.signOutDirectProvider(any()) }
            onNodeWithContentDescription("Continue with ChatGPT").assertIsEnabled()
            onNodeWithContentDescription("Sign out of ChatGPT").assertIsNotEnabled()
        }

    @Test
    fun `API key mode exposes no OAuth actions and does not start authorization`() =
        runComposeUiTest {
            render(profile().copy(directAuthMode = DirectProviderAuthMode.API_KEY))

            onNodeWithContentDescription("Continue with ChatGPT").assertDoesNotExist()
            onNodeWithContentDescription("Sign out of ChatGPT").assertDoesNotExist()
            coVerify(exactly = 0) { port.beginDirectSignIn(any()) }
            assertEquals(false, opened.isCompleted)
        }

    private fun ComposeUiTest.render(profile: ProviderProfile) =
        renderProfile({ profile }) { id, reference ->
            changed.complete(id to reference)
        }

    private fun ComposeUiTest.renderProfile(
        profile: () -> ProviderProfile,
        storedProfile: () -> ProviderProfile? = profile,
        onChanged: (String, String?) -> Unit,
    ) {
        every { port.getProvider(any()) } answers { storedProfile() }
        setContent {
            CompositionLocalProvider(
                LocalUriHandler provides
                    object : UriHandler {
                        override fun openUri(uri: String) {
                            opened.complete(uri)
                        }
                    },
            ) {
                MaterialTheme {
                    Column {
                        DirectAuthorizationControls(profile(), port, onChanged)
                    }
                }
            }
        }
        waitUntil { onAllNodesWithText("Loading ChatGPT sign-in status...").fetchSemanticsNodes().isEmpty() }
    }

    private fun profile() =
        ProviderProfile(
            "profile",
            "Direct",
            ProviderAdapter.CODEX_DIRECT,
            "https://api.openai.com",
            directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
        )
}
