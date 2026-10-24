package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.conversation.models.ConversationModelPicker
import de.heckenmann.visualagent.ui.conversation.models.conversationModelOptions
import de.heckenmann.visualagent.ui.conversation.models.rememberConversationModelState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals

/** Covers the immediate selector without browsers, provider inference, or timing assertions. */
class ConversationModelPickerTest {
    @get:Rule val compose = createComposeRule()
    private val providers = mockk<ProviderPort>(relaxed = true)
    private val settings = mockk<SettingsPort>(relaxed = true)
    private val profile =
        ProviderProfile(
            "main",
            "Main provider",
            ProviderAdapter.OLLAMA,
            "http://localhost",
            models =
                listOf(
                    ProviderModel("active"),
                    ProviderModel("unknown"),
                    ProviderModel(
                        "blocked",
                        capabilityOverrides =
                            mapOf(
                                "chat" to false,
                            ),
                    ),
                ),
        )

    @Test fun `search prioritizes favorites and preserves provider identities and unknown capabilities`() {
        val other = profile.copy(id = "second", name = "Second provider", models = listOf(ProviderModel("unknown")))
        val results =
            conversationModelOptions(listOf(profile, other, profile.copy(id = "disabled", enabled = false)), listOf("unknown"), "unknown")
        assertEquals(listOf("main", "second"), results.map { it.provider.id })
        assertEquals(true, results.all { it.favorite })
        assertEquals(1, conversationModelOptions(listOf(profile), emptyList(), "block").size)
        assertEquals(emptyList(), conversationModelOptions(listOf(profile), emptyList(), "absent"))
    }

    @Test fun `unsupported chat is disabled while unknown chat remains selectable and failures keep the saved model`() {
        every { providers.listProviders() } returns listOf(profile)
        every { settings.snapshot() } returns SettingsSnapshot(providerId = "main", modelId = "active")
        coEvery { settings.selectConversationModel("main", "unknown") } throws IllegalStateException("Selection failed")
        compose.setContent { MaterialTheme { ConversationModelPicker(rememberConversationModelState(providers, settings)) {} } }
        ready()
        compose.onNodeWithContentDescription("Select conversation model").performClick()
        compose.onNodeWithContentDescription("Use Main provider: blocked").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Use Main provider: unknown").performClick()
        compose.waitUntil { runCatching { compose.onNodeWithText("Operation failed").assertExists() }.isSuccess }
        compose.onNodeWithText("active · Main provider").assertExists()
        coVerify(exactly = 1) { settings.selectConversationModel("main", "unknown") }
    }

    @Test fun `picker search empty state and manage providers action are available without sending input`() {
        every { providers.listProviders() } returns listOf(profile)
        every { settings.snapshot() } returns SettingsSnapshot(providerId = "main", modelId = "active")
        var opened = false
        compose.setContent {
            MaterialTheme {
                ConversationModelPicker(
                    rememberConversationModelState(providers, settings),
                ) { opened = true }
            }
        }
        ready()
        compose.onNodeWithContentDescription("Select conversation model").performClick()
        compose.onNodeWithText("Search models").performTextInput("no such model")
        compose.onNodeWithText("No matching models").assertExists()
        compose.onNodeWithContentDescription("Manage providers").performClick()
        assertEquals(true, opened)
        coVerify(exactly = 0) { settings.selectConversationModel(any(), any()) }
    }

    @Test fun `external catalog changes update the displayed model without replacing the user's draft`() {
        var listener: () -> Unit = {}
        var snapshot = SettingsSnapshot(providerId = "main", modelId = "active")
        every { settings.snapshot() } answers { snapshot }
        every { providers.listProviders() } returns listOf(profile)
        every { providers.addChangeListener(any()) } answers {
            listener = firstArg()
            AutoCloseable {}
        }
        compose.setContent {
            MaterialTheme {
                var draft by remember { mutableStateOf("") }
                Column {
                    ConversationModelPicker(rememberConversationModelState(providers, settings)) {}
                    OutlinedTextField(draft, onValueChange = { draft = it }, label = { androidx.compose.material3.Text("Draft") })
                }
            }
        }
        ready()
        compose.onNodeWithText("Draft").performTextInput("Retain my input")
        snapshot = snapshot.copy(modelId = "unknown")
        compose.runOnIdle { listener() }
        compose.waitUntil { runCatching { compose.onNodeWithText("unknown · Main provider").assertExists() }.isSuccess }
        compose.onNodeWithText("Retain my input").assertExists()
        coVerify(exactly = 0) { settings.selectConversationModel(any(), any()) }
    }

    private fun ready() {
        compose.waitUntil { runCatching { compose.onNodeWithText("active · Main provider").assertExists() }.isSuccess }
    }

    @Test fun `blocked catalog IO leaves text input responsive and recomposition does not query again`() {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val revision = mutableStateOf(0)
        every { providers.listProviders() } answers {
            entered.complete(Unit)
            release.await()
            listOf(profile)
        }
        every { settings.snapshot() } returns SettingsSnapshot(providerId = "main", modelId = "active")
        try {
            compose.setContent {
                MaterialTheme {
                    var draft by remember { mutableStateOf("") }
                    Column {
                        androidx.compose.material3.Text("Revision ${revision.value}")
                        ConversationModelPicker(rememberConversationModelState(providers, settings)) {}
                        OutlinedTextField(draft, onValueChange = { draft = it }, label = { androidx.compose.material3.Text("Input") })
                    }
                }
            }
            compose.waitUntil { entered.isCompleted }
            compose.onNodeWithText("Input").performTextInput("Still responsive")
            compose.onNodeWithText("Still responsive").assertExists()
            compose.runOnIdle { revision.value++ }
            compose.onNodeWithText("Revision 1").assertExists()
        } finally {
            release.countDown()
        }
        ready()
        verify(exactly = 1) { providers.listProviders() }
    }
}
