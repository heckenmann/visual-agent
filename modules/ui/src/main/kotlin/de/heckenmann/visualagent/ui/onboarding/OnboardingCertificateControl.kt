package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CertificateTrustPort
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.settings.certificates.certificateImportModal

/** Holds the import dialog separately from the provider draft and step. */
internal class OnboardingCertificateImport(
    private val certificates: CertificateTrustPort?,
) {
    val available: Boolean get() = certificates != null
    var modal by mutableStateOf<ComposeContentModal?>(null)
        private set
    var importRevision by mutableIntStateOf(0)
        private set
    private var failedEndpoint by mutableStateOf<String?>(null)

    fun open() {
        certificates?.let { modal = certificateImportModal(it) { importRevision++ } }
    }

    fun canReview(baseUrl: String?): Boolean = baseUrl != null && baseUrl == failedEndpoint

    fun offerReview(baseUrl: String?) {
        val port = certificates ?: return
        if (baseUrl == null) return
        failedEndpoint = baseUrl
        modal =
            endpointCertificateReviewModal(port, baseUrl) {
                failedEndpoint = null
                importRevision++
            }
    }

    fun clearReview() {
        failedEndpoint = null
    }

    fun dismiss() {
        modal = null
    }
}

/** Opens CA import without leaving the provider step or replacing its draft. */
@Composable
internal fun OnboardingCertificateControl(
    state: OnboardingCertificateImport,
    busy: Boolean,
    baseUrl: String?,
) {
    if (!state.available) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = state::open) { Text("Import CA certificate") }
        if (state.canReview(baseUrl)) {
            OutlinedButton(enabled = !busy, onClick = { state.offerReview(baseUrl) }) { Text("Review server certificates") }
        }
    }
    if (state.importRevision > 0) Text("Certificate trust updated on the connected server.")
}
