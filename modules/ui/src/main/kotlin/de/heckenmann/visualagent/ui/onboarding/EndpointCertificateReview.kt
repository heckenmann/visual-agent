package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CertificateTrustPort
import de.heckenmann.visualagent.protocol.PresentedCertificate
import de.heckenmann.visualagent.protocol.PresentedCertificateChain
import de.heckenmann.visualagent.protocol.PresentedCertificateKind
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ModalDialogLayout
import de.heckenmann.visualagent.ui.modal.modalPrimaryButton
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton
import de.heckenmann.visualagent.ui.settings.certificates.certificateRequest
import kotlinx.coroutines.launch

internal fun endpointCertificateReviewModal(
    certificates: CertificateTrustPort,
    baseUrl: String,
    onImported: () -> Unit,
): ComposeContentModal {
    val importing = mutableStateOf(false)
    return ComposeContentModal("Review server certificates", canDismiss = { !importing.value }) { dismiss ->
        EndpointCertificateReview(certificates, baseUrl, importing, dismiss, onImported)
    }
}

@Composable
private fun EndpointCertificateReview(
    certificates: CertificateTrustPort,
    baseUrl: String,
    importInProgress: MutableState<Boolean>,
    dismiss: () -> Unit,
    onImported: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var chain by remember { mutableStateOf<PresentedCertificateChain?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by importInProgress
    LaunchedEffect(certificates, baseUrl, revision) {
        error = null
        chain = null
        selected = emptySet()
        certificateRequest { certificates.reviewEndpoint(baseUrl) }
            .onSuccess { reviewed ->
                chain = reviewed
                selected =
                    reviewed.certificates
                        .filter { it.kind == PresentedCertificateKind.ROOT_CA && it.canImport }
                        .map { it.certificate.sha256 }
                        .toSet()
            }.onFailure { error = it.toUiErrorMessage() }
    }
    ModalDialogLayout(
        body = {
            if (chain == null && error == null) Text("Reading the HTTPS certificate chain…")
            chain?.let { reviewed ->
                Text("Certificates presented by ${reviewed.endpoint}", style = MaterialTheme.typography.titleMedium)
                Text("Compare the SHA-256 fingerprints with a trusted source before importing.")
                Text(
                    "CA certificates apply to outbound TLS on this Visual Agent server. The server certificate applies only to ${reviewed.endpoint}.",
                )
                if (reviewed.certificates.none { it.kind == PresentedCertificateKind.ROOT_CA }) {
                    Text("The server did not send a root CA. You can import it separately from a trusted PEM file.")
                }
                reviewed.certificates.asReversed().forEach { entry ->
                    HorizontalDivider()
                    val fingerprint = entry.certificate.sha256
                    PresentedCertificateRow(entry, fingerprint in selected, !importing) {
                        selected = if (it) selected + fingerprint else selected - fingerprint
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        },
        footer = {
            modalSecondaryButton(label = "Cancel", enabled = !importing, onClick = dismiss)
            if (error != null) modalSecondaryButton(label = "Reload certificates", onClick = { revision++ })
            modalPrimaryButton(
                label = if (importing) "Importing…" else "Import selected certificates",
                enabled = !importing && error == null && selected.isNotEmpty() && chain?.certificates?.firstOrNull()?.canImport == true,
                onClick = {
                    val reviewed = chain ?: return@modalPrimaryButton
                    if (importing) return@modalPrimaryButton
                    val selectedFingerprints = selected
                    importing = true
                    scope.launch {
                        val result =
                            try {
                                certificateRequest {
                                    certificates.importEndpointCertificates(
                                        baseUrl,
                                        reviewed.certificates.map { it.certificate.sha256 },
                                        selectedFingerprints,
                                    )
                                }
                            } finally {
                                importing = false
                            }
                        result
                            .onSuccess {
                                onImported()
                                dismiss()
                            }.onFailure { error = it.toUiErrorMessage() }
                    }
                },
            )
        },
    )
}

@Composable
private fun PresentedCertificateRow(
    entry: PresentedCertificate,
    checked: Boolean,
    editable: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val label =
        when (entry.kind) {
            PresentedCertificateKind.SERVER -> "Server certificate"
            PresentedCertificateKind.INTERMEDIATE_CA -> "Intermediate CA"
            PresentedCertificateKind.ROOT_CA -> if (entry.canImport) "Root CA (recommended)" else "Root CA"
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier =
                Modifier.fillMaxWidth().toggleable(
                    value = checked,
                    enabled = editable && entry.canImport,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = checked, enabled = editable && entry.canImport, onCheckedChange = null)
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
        val certificate = entry.certificate
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Subject: ${certificate.subject}")
                Text("Issuer: ${certificate.issuer}")
                Text("Valid until: ${certificate.notAfter}")
                Text("SHA-256: ${certificate.sha256}", fontFamily = FontFamily.Monospace)
            }
        }
        if (!entry.canImport) Text("This certificate is ineligible for import.", color = MaterialTheme.colorScheme.error)
    }
}
