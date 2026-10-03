package de.heckenmann.visualagent.ui.application

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.protocol.ModelDetails
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotEquals

/** Verifies asynchronous warning metadata and source-keyed rather than render-keyed refreshes. */
class ModelCapabilityWarningLoadingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `blocked catalog leaves UI responsive and recomposition does not repeat reads`() {
        val providers = mockk<ProviderPort>()
        val contextLength = mutableStateOf(8_192)
        val revision = mutableStateOf(0)
        val started = CompletableFuture<Unit>()
        val release = CompletableFuture<Unit>()
        var uiThread: Thread? = null
        compose.runOnIdle { uiThread = Thread.currentThread() }
        every { providers.activeProviderId() } answers {
            assertNotEquals(uiThread, Thread.currentThread())
            started.complete(Unit)
            release.get(10, TimeUnit.SECONDS)
            "local"
        }
        every { providers.activeModelId() } answers {
            assertNotEquals(uiThread, Thread.currentThread())
            "model"
        }
        every { providers.getProvider("local") } answers {
            assertNotEquals(uiThread, Thread.currentThread())
            ProviderProfile("local", "Local", ProviderAdapter.OLLAMA, "", models = listOf(ProviderModel("model")))
        }
        coEvery { providers.modelDetails("local", "model") } coAnswers {
            assertNotEquals(uiThread, Thread.currentThread())
            ModelDetails("model", "", contextLimit = 2_048)
        }
        try {
            compose.setContent {
                val warnings = rememberModelCapabilityWarnings(providers, contextLength.value, revision.value, "", "")
                Text(warnings.context?.label ?: "No context warning")
            }
            started.get(10, TimeUnit.SECONDS)
            compose.runOnIdle { contextLength.value = 1_024 }
            compose.onNodeWithText("Context 1024").assertExists()
            release.complete(Unit)
            compose.runOnIdle { contextLength.value = 8_192 }
            compose.waitUntil {
                runCatching { compose.onNodeWithText("Context 2048").fetchSemanticsNode() }.isSuccess
            }
            repeat(10) { index -> compose.runOnIdle { contextLength.value = 8_192 + index } }
            verify(exactly = 1) { providers.activeProviderId() }
            verify(exactly = 1) { providers.activeModelId() }
            verify(exactly = 1) { providers.getProvider("local") }
            coVerify(exactly = 1) { providers.modelDetails("local", "model") }

            compose.runOnIdle { revision.value++ }
            compose.waitUntil {
                runCatching { verify(exactly = 2) { providers.getProvider("local") } }.isSuccess
            }
        } finally {
            release.complete(Unit)
        }
    }
}
