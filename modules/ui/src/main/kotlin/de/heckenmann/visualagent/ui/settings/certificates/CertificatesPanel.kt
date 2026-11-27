package de.heckenmann.visualagent.ui.settings.certificates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CertificateTrustPort
import de.heckenmann.visualagent.protocol.TrustedCertificate
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.modal.ModalDialogLayout
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlinx.coroutines.launch

/** Manages additional CA anchors and endpoint-scoped server certificates. */
@Composable
internal fun CertificatesPanel(
    certificates: CertificateTrustPort?,
    modals: ComposeModalRequester,
) {
    if (certificates == null) {
        Text("Certificate management is unavailable on this Visual Agent server connection.", Modifier.padding(16.dp))
        return
    }
    val scope = rememberCoroutineScope()
    var entries by remember(certificates) { mutableStateOf<List<TrustedCertificate>?>(null) }
    var revision by remember(certificates) { mutableIntStateOf(0) }
    var status by remember(certificates) { mutableStateOf<String?>(null) }
    var listError by remember(certificates) { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf(false) }
    LaunchedEffect(certificates, revision) {
        listError = null
        certificateRequest(certificates::list)
            .onSuccess { entries = it }
            .onFailure { listError = it.toUiErrorMessage() }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Trusted certificates", style = MaterialTheme.typography.titleLarge)
        Text("Changes apply to the connected Visual Agent server. System trust roots and server identity keys are managed separately.")
        Text("Provider requests use the updated trust store on retry. Restart the server to reload clients that keep TLS connections open.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !removing, onClick = {
                modals.request(
                    certificateImportModal(certificates) {
                        status = "CA certificate imported."
                        revision++
                    },
                )
            }) { Text("Import CA certificate") }
            OutlinedButton(onClick = { revision++ }) { Text("Refresh certificates") }
        }
        listError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        status?.let { Text(it) }
        when {
            entries == null && listError == null -> Text("Loading trusted certificates…")
            entries?.isEmpty() == true -> Text("No additional certificates are trusted.")
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries.orEmpty(), key = TrustedCertificate::alias) { certificate ->
                Text(certificate.endpoint ?: certificate.alias, style = MaterialTheme.typography.titleMedium)
                Text(certificate.subject)
                Text(certificate.endpoint?.let { "Server certificate for $it" } ?: "CA certificate")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { modals.request(certificateDetailsModal(certificate)) }) { Text("Inspect") }
                    OutlinedButton(onClick = { modals.request(certificateExportModal(certificates, certificate)) }) { Text("Export") }
                    OutlinedButton(
                        enabled = !removing,
                        onClick = {
                            modals.request(
                                removeCertificateModal(certificate) {
                                    scope.launch {
                                        removing = true
                                        certificateRequest { certificates.removeCertificate(certificate.alias, certificate.sha256) }
                                            .onSuccess { removed ->
                                                status =
                                                    if (removed) {
                                                        "Certificate removed."
                                                    } else {
                                                        "The certificate was removed or changed. Review the current trust store."
                                                    }
                                            }.onFailure { status = it.toUiErrorMessage() }
                                        revision++
                                        removing = false
                                    }
                                },
                            )
                        },
                    ) { Text("Remove") }
                }
            }
        }
    }
}

private fun removeCertificateModal(
    certificate: TrustedCertificate,
    onConfirm: () -> Unit,
): ComposeConfirmationModal =
    ComposeConfirmationModal(
        title = "Remove trusted certificate?",
        message = "Remove trust for ${certificate.endpoint ?: certificate.alias}?\nSHA-256: ${certificate.sha256}",
        confirmDescription = "Remove certificate",
        onConfirm = onConfirm,
    )

private fun certificateDetailsModal(certificate: TrustedCertificate): ComposeContentModal =
    ComposeContentModal(if (certificate.endpoint == null) "Trusted CA certificate" else "Trusted server certificate") { dismiss ->
        ModalDialogLayout(
            body = {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(certificate.endpoint?.let { "Endpoint: $it" } ?: "Alias: ${certificate.alias}")
                        Text("Subject: ${certificate.subject}")
                        Text("Issuer: ${certificate.issuer}")
                        Text("Valid from: ${certificate.notBefore}")
                        Text("Valid until: ${certificate.notAfter}")
                        Text("SHA-256: ${certificate.sha256}", fontFamily = FontFamily.Monospace)
                    }
                }
            },
            footer = { modalSecondaryButton(label = "Close", onClick = dismiss) },
        )
    }

private fun certificateExportModal(
    certificates: CertificateTrustPort,
    certificate: TrustedCertificate,
): ComposeContentModal =
    ComposeContentModal("Export certificate") { dismiss ->
        CertificateExportDialog(certificates, certificate, dismiss)
    }

@Composable
private fun CertificateExportDialog(
    certificates: CertificateTrustPort,
    certificate: TrustedCertificate,
    dismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pem by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val saver =
        rememberFileSaverLauncher(
            FileKitDialogSettings.createDefault(),
            onError = { status = "Could not open the save dialog." },
        ) { selected: PlatformFile? ->
            val certificate = pem
            if (selected != null && certificate != null) {
                scope.launch {
                    certificateRequest { selected.file.writeText(certificate, Charsets.US_ASCII) }
                        .onSuccess { status = "Public certificate saved." }
                        .onFailure { status = "Could not save the public certificate." }
                }
            }
        }
    LaunchedEffect(certificates, certificate.alias) {
        certificateRequest { certificates.exportCertificate(certificate.alias) }
            .onSuccess { exported ->
                pem = exported
                if (exported == null) status = "The certificate is no longer in the trust store."
            }.onFailure { status = it.toUiErrorMessage() }
    }
    ModalDialogLayout(
        body = {
            Text("Export the public certificate for ${certificate.endpoint ?: certificate.alias} as a PEM file.")
            pem?.let { publicPem ->
                OutlinedTextField(
                    value = publicPem,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("PEM certificate") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 12,
                )
            }
            status?.let { Text(it) }
        },
        footer = {
            modalSecondaryButton(label = "Close", onClick = dismiss)
            OutlinedButton(
                enabled = pem != null,
                onClick = {
                    saver.launch(
                        suggestedName =
                            if (certificate.endpoint ==
                                null
                            ) {
                                certificate.alias
                            } else {
                                "server-certificate"
                            },
                        defaultExtension = "pem",
                    )
                },
            ) {
                Text("Save PEM")
            }
        },
    )
}
