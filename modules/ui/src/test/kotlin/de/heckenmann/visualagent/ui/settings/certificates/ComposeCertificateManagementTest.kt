package de.heckenmann.visualagent.ui.settings.certificates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CertificateTrustPort
import de.heckenmann.visualagent.protocol.PresentedCertificateChain
import de.heckenmann.visualagent.protocol.TrustedCertificate
import de.heckenmann.visualagent.ui.modal.ComposeModal
import de.heckenmann.visualagent.ui.modal.ComposeModalHost
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Exercises the connected server's certificate controls and removal confirmation. */
class ComposeCertificateManagementTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Settings imports inspects exports and confirms removal`() {
        val port = TestCertificateTrustPort()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 640.dp)) {
                    var modal by remember { mutableStateOf<ComposeModal?>(null) }
                    CertificatesPanel(port, ComposeModalRequester { modal = it })
                    ComposeModalHost(modal, onDismiss = { modal = null })
                }
            }
        }
        waitFor("No additional certificates are trusted.")
        compose.onNodeWithText("Import CA certificate").performClick()
        compose.onNodeWithText("Certificate alias").performTextReplacement("organization")
        compose.onNodeWithText("Public CA certificate (PEM)").performTextReplacement(FIXTURE_PEM)
        compose.onNodeWithText("Trust certificate").performClick()
        waitFor("organization")
        compose.onNodeWithText("Inspect").performClick()
        compose.onNodeWithText("Subject: CN=Test CA").assertIsDisplayed()
        compose.onNodeWithText("Issuer: CN=Test Issuer").assertIsDisplayed()
        compose.onNodeWithText("Valid from: 2026-01-01T00:00:00Z").assertIsDisplayed()
        compose.onNodeWithText("Valid until: 2027-01-01T00:00:00Z").assertIsDisplayed()
        compose.onNodeWithText("SHA-256: ${"a".repeat(64)}").assertIsDisplayed()
        compose.onAllNodesWithText("Close")[0].performClick()

        compose.onNodeWithText("Export").performClick()
        waitFor(FIXTURE_PEM)
        compose.onNodeWithText("Save PEM").assertIsDisplayed()
        compose.onAllNodesWithText("Close")[0].performClick()

        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertNotNull(port.entry.get())
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Remove certificate").performClick()
        waitFor("No additional certificates are trusted.")
        assertNull(port.entry.get())
    }

    private fun waitFor(label: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

internal const val FIXTURE_PEM = "-----BEGIN CERTIFICATE-----\nZml4dHVyZQ==\n-----END CERTIFICATE-----"

internal class TestCertificateTrustPort(
    private val chain: PresentedCertificateChain? = null,
    private val beforeEndpointImport: () -> Unit = {},
) : CertificateTrustPort {
    val entry = AtomicReference<TrustedCertificate?>()
    val importedSelection = AtomicReference<Set<String>?>()

    override fun list(): List<TrustedCertificate> = listOfNotNull(entry.get())

    override fun importCa(
        alias: String,
        pem: String,
    ): TrustedCertificate {
        check(pem == FIXTURE_PEM)
        val certificate =
            TrustedCertificate(alias, "CN=Test CA", "CN=Test Issuer", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z", "a".repeat(64))
        entry.set(certificate)
        return certificate
    }

    override fun exportCertificate(alias: String): String? = FIXTURE_PEM.takeIf { entry.get()?.alias == alias }

    override fun removeCertificate(
        alias: String,
        sha256: String,
    ): Boolean {
        val current = entry.get() ?: return false
        return current.alias == alias && current.sha256 == sha256 && entry.compareAndSet(current, null)
    }

    override fun reviewEndpoint(baseUrl: String): PresentedCertificateChain = checkNotNull(chain)

    override fun importEndpointCertificates(
        baseUrl: String,
        reviewedFingerprints: List<String>,
        selectedFingerprints: Set<String>,
    ) {
        check(baseUrl == "https://models.example")
        check(reviewedFingerprints == checkNotNull(chain).certificates.map { it.certificate.sha256 })
        beforeEndpointImport()
        importedSelection.set(selectedFingerprints)
    }
}
