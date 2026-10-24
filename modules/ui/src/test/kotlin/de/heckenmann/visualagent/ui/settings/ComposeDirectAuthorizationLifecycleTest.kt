package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import de.heckenmann.visualagent.protocol.DirectProviderAuthMode
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertTrue

/** Verifies catalog reads never block Compose or repeat merely because its presentation changes. */
@OptIn(ExperimentalTestApi::class)
class ComposeDirectAuthorizationLifecycleTest {
    @Test
    fun `blocked registration lookup permits UI progress and recomposition does not reload the catalog`() =
        runComposeUiTest {
            val profile =
                ProviderProfile(
                    "profile",
                    "Direct",
                    ProviderAdapter.CODEX_DIRECT,
                    "https://api.openai.com",
                    directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
                )
            val port = mockk<ProviderPort>()
            val uiThread = AtomicReference<Thread>()
            val lookupStarted = CompletableDeferred<Boolean>()
            val referencePublished = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            var clicks by mutableStateOf(0)
            every { port.addChangeListener(any()) } returns AutoCloseable { }
            every { port.getProvider(profile.id) } answers {
                val background = Thread.currentThread() !== uiThread.get()
                lookupStarted.complete(background)
                if (background) release.await()
                profile.copy(credentialReference = "server-registration")
            }
            try {
                setContent {
                    uiThread.set(Thread.currentThread())
                    CompositionLocalProvider(
                        LocalUriHandler provides
                            object : UriHandler {
                                override fun openUri(uri: String) = error("No browser launch is expected")
                            },
                    ) {
                        MaterialTheme {
                            Column {
                                Button(onClick = { clicks++ }) { Text("UI progress: $clicks") }
                                DirectAuthorizationControls(profile, port) { _, _ -> referencePublished.complete(Unit) }
                            }
                        }
                    }
                }
                awaitIdle()
                assertTrue(lookupStarted.await(), "Registration lookup must run off the UI thread")
                onNodeWithText("UI progress: 0").performClick()
                onNodeWithText("UI progress: 1").assertExists()
                onNodeWithContentDescription("Continue with ChatGPT").assertIsNotEnabled()
                onNodeWithContentDescription("Sign out of ChatGPT").assertIsNotEnabled()
                release.countDown()
                waitUntil { referencePublished.isCompleted }
                awaitIdle()
                onNodeWithContentDescription("Continue with ChatGPT").assertIsNotEnabled()
                onNodeWithContentDescription("Sign out of ChatGPT").assertIsEnabled()
                onNodeWithText("UI progress: 1").performClick()
                onNodeWithText("UI progress: 2").assertExists()
                verify(exactly = 1) { port.getProvider(profile.id) }
            } finally {
                release.countDown()
            }
        }
}
