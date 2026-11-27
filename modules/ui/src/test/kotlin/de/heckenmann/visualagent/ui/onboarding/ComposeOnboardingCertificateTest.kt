package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationAvailability
import de.heckenmann.visualagent.protocol.OnboardingPort
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingValidationCode
import de.heckenmann.visualagent.protocol.OnboardingValidationResult
import de.heckenmann.visualagent.protocol.PresentedCertificate
import de.heckenmann.visualagent.protocol.PresentedCertificateChain
import de.heckenmann.visualagent.protocol.PresentedCertificateKind
import de.heckenmann.visualagent.protocol.ProtocolErrorCategory
import de.heckenmann.visualagent.protocol.ProtocolOperationException
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.TrustedCertificate
import de.heckenmann.visualagent.protocol.UserFacingError
import de.heckenmann.visualagent.ui.settings.certificates.FIXTURE_PEM
import de.heckenmann.visualagent.ui.settings.certificates.TestCertificateTrustPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Exercises recovery from a certificate trust error with the same staged provider. */
class ComposeOnboardingCertificateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `import is reachable before discovery and after a trust error without discarding the draft`() {
        val certificates = TestCertificateTrustPort()
        val onboarding = mockk<OnboardingPort>()
        val drafts = CopyOnWriteArrayList<OnboardingProviderDraft>()
        val error = "Import the provider's public CA certificate on the connected Visual Agent server, then retry."
        every { onboarding.providers() } returns emptyList()
        every { onboarding.agents() } returns emptyList()
        every { onboarding.agentCreationAvailability() } returns OnboardingAgentCreationAvailability(available = true)
        coEvery { onboarding.discoverModels(any()) } coAnswers {
            drafts += firstArg<OnboardingProviderDraft>()
            if (certificates.entry.get() == null) {
                throw ProtocolOperationException(UserFacingError(ProtocolErrorCategory.PROVIDER, "Provider TLS verification failed", error))
            }
            listOf(ProviderModel("private-model"))
        }
        coEvery { onboarding.validate(any(), "private-model") } returns
            OnboardingValidationResult(true, OnboardingValidationCode.SUCCESS, "Ready", "fingerprint")
        coEvery { onboarding.finish(any(), any(), "fingerprint") } coAnswers {
            assertEquals(drafts.first(), firstArg<OnboardingProviderDraft>())
        }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(880.dp, 600.dp)) {
                    ComposeOnboardingWizard(onboarding, automatic = false, onFinished = {}, certificates = certificates)
                }
            }
        }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Add provider").performClick()
        compose.onNodeWithText("Import CA certificate").assertIsDisplayed()
        compose.onNodeWithText("Provider name").performTextReplacement("Organization API")
        compose.onNodeWithText("Provider endpoint", substring = true).performTextReplacement("https://models.example")
        compose.onNodeWithText("Continue").performClick()
        waitFor(error)

        compose.onNodeWithText("Import CA certificate").performClick()
        compose.onNodeWithText("Certificate alias").performTextReplacement("organization")
        compose.onNodeWithText("Public CA certificate (PEM)").performTextReplacement(FIXTURE_PEM)
        compose.onNodeWithText("Trust certificate").performClick()
        waitFor("private-model")
        assertEquals("Organization API", drafts.first().name)
        assertEquals("https://models.example", drafts.first().baseUrl)
        assertEquals(listOf(drafts.first(), drafts.first()), drafts.toList())
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Review").assertIsDisplayed()
        compose.onNodeWithText("Save and continue").performClick()
        waitFor("Create your first sub-agent")
    }

    @Test
    fun `discovery offers root intermediate and server trust with explicit confirmation`() {
        val leaf = presented(PresentedCertificateKind.SERVER, "a")
        val intermediate = presented(PresentedCertificateKind.INTERMEDIATE_CA, "b")
        val root = presented(PresentedCertificateKind.ROOT_CA, "c")
        val importStarted = CountDownLatch(1)
        val finishImport = CountDownLatch(1)
        val port =
            TestCertificateTrustPort(PresentedCertificateChain("models.example:443", listOf(leaf, intermediate, root))) {
                importStarted.countDown()
                check(finishImport.await(10, TimeUnit.SECONDS))
            }
        val onboarding = mockk<OnboardingPort>()
        every { onboarding.providers() } returns emptyList()
        coEvery { onboarding.discoverModels(any()) } coAnswers {
            if (port.importedSelection.get() == null) throw trustRequired()
            listOf(ProviderModel("private-model"))
        }
        openProvider(onboarding, port)

        waitFor("Root CA (recommended)")
        compose.onNodeWithText("SHA-256: ${root.certificate.sha256}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Root CA (recommended)").assertIsOn()
        compose.onNodeWithText("Intermediate CA").assertIsOff()
        compose.onNodeWithText("Server certificate").assertIsOff()
        assertNull(port.importedSelection.get())
        compose.onNodeWithText("Intermediate CA").performScrollTo().performClick()
        compose.onNodeWithText("Server certificate").performScrollTo().performClick()
        try {
            compose.onNodeWithText("Import selected certificates").performClick()
            compose.waitUntil(timeoutMillis = 5_000) { importStarted.count == 0L }
            compose.onNodeWithContentDescription("Close").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Cancel").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Importing…").performKeyInput { pressKey(Key.Escape) }
            compose.onNodeWithText("Importing…").assertIsDisplayed()
        } finally {
            finishImport.countDown()
        }
        waitFor("private-model")
        assertEquals(setOf(leaf.certificate.sha256, intermediate.certificate.sha256, root.certificate.sha256), port.importedSelection.get())
    }

    @Test
    fun `validation without a presented root leaves trust unselected`() {
        val leaf = presented(PresentedCertificateKind.SERVER, "a")
        val intermediate = presented(PresentedCertificateKind.INTERMEDIATE_CA, "b")
        val port = TestCertificateTrustPort(PresentedCertificateChain("models.example:443", listOf(leaf, intermediate)))
        val onboarding = mockk<OnboardingPort>()
        every { onboarding.providers() } returns emptyList()
        coEvery { onboarding.discoverModels(any()) } returns listOf(ProviderModel("private-model"))
        coEvery { onboarding.validate(any(), "private-model") } returns
            OnboardingValidationResult(
                false,
                OnboardingValidationCode.TLS_VERIFICATION_FAILED,
                "Unknown CA",
                certificateTrustRequired = true,
            )
        openProvider(onboarding, port)
        waitFor("private-model")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Save and continue").performClick()
        waitFor("The server did not send a root CA. You can import it separately from a trusted PEM file.")
        compose.onNodeWithText("Import selected certificates").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithText("Review server certificates").assertIsDisplayed()
        compose.onNodeWithText("Import CA certificate").assertIsDisplayed()
        assertNull(port.importedSelection.get())
    }

    private fun openProvider(
        onboarding: OnboardingPort,
        port: TestCertificateTrustPort,
    ) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(880.dp, 680.dp)) {
                    ComposeOnboardingWizard(onboarding, automatic = false, onFinished = {}, certificates = port)
                }
            }
        }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Add provider").performClick()
        compose.onNodeWithText("Provider name").performTextReplacement("Organization API")
        compose.onNodeWithText("Provider endpoint", substring = true).performTextReplacement("https://models.example")
        compose.onNodeWithText("Continue").performClick()
    }

    private fun trustRequired(): ProtocolOperationException =
        ProtocolOperationException(
            UserFacingError(ProtocolErrorCategory.PROVIDER, "Provider TLS verification failed", "Unknown CA"),
            certificateTrustRequired = true,
        )

    private fun presented(
        kind: PresentedCertificateKind,
        digit: String,
    ): PresentedCertificate =
        PresentedCertificate(
            TrustedCertificate(digit, "CN=${kind.name}", "CN=Issuer", "2026-01-01", "2027-01-01", digit.repeat(64)),
            kind,
            canImport = true,
        )

    private fun waitFor(label: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
