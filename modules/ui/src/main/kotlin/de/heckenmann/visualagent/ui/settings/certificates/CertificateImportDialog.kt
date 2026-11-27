package de.heckenmann.visualagent.ui.settings.certificates

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.protocol.CertificateTrustPort
import de.heckenmann.visualagent.protocol.MAX_TRUST_CERTIFICATE_PEM_BYTES
import de.heckenmann.visualagent.protocol.isSinglePublicCertificatePem
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ModalDialogLayout
import de.heckenmann.visualagent.ui.modal.modalPrimaryButton
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shares one explicit CA import confirmation between Settings and onboarding. */
internal fun certificateImportModal(
    certificates: CertificateTrustPort,
    onImported: () -> Unit,
): ComposeContentModal {
    val importing = mutableStateOf(false)
    return ComposeContentModal("Import CA certificate", canDismiss = { !importing.value }) { dismiss ->
        CertificateImportForm(certificates, importing, dismiss, onImported)
    }
}

@Composable
private fun CertificateImportForm(
    certificates: CertificateTrustPort,
    importInProgress: MutableState<Boolean>,
    dismiss: () -> Unit,
    onImported: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var alias by remember { mutableStateOf("") }
    var pem by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by importInProgress
    var reading by remember { mutableStateOf(false) }
    val busy = reading || importing
    val picker =
        rememberFilePickerLauncher { selected: PlatformFile? ->
            if (selected != null) {
                scope.launch {
                    reading = true
                    certificateRequest {
                        selected.file.inputStream().use { input ->
                            val bytes = input.readNBytes(MAX_TRUST_CERTIFICATE_PEM_BYTES + 1)
                            require(bytes.size <= MAX_TRUST_CERTIFICATE_PEM_BYTES)
                            bytes.toString(Charsets.US_ASCII)
                        }
                    }.onSuccess {
                        pem = it
                        error = null
                    }.onFailure { error = "Choose a readable PEM file of at most 128 KiB." }
                    reading = false
                }
            }
        }
    ModalDialogLayout(
        body = {
            Text("This changes the CA trust store on the connected Visual Agent server. Import only a public CA you trust.")
            Text(
                "Provider connections on that server can trust certificates issued by this CA. Server identity and private keys are separate.",
            )
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text("Certificate alias") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedButton(enabled = !busy, onClick = { picker.launch() }) { Text("Choose PEM file") }
            OutlinedTextField(
                value = pem,
                onValueChange = { pem = it },
                label = { Text("Public CA certificate (PEM)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                minLines = 3,
                maxLines = 5,
            )
            if (pem.length > MAX_TRUST_CERTIFICATE_PEM_BYTES) {
                Text("Certificate input exceeds 128 KiB.", color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        },
        footer = {
            modalSecondaryButton(label = "Cancel", enabled = !importing, onClick = dismiss)
            modalPrimaryButton(
                label = "Trust certificate",
                enabled = !busy && alias.isNotBlank() && pem.isNotBlank() && pem.length <= MAX_TRUST_CERTIFICATE_PEM_BYTES,
                onClick = {
                    if (reading || importing) return@modalPrimaryButton
                    importing = true
                    val submittedPem = pem
                    val submittedAlias = alias
                    scope.launch {
                        val result =
                            try {
                                if (!withContext(Dispatchers.Default) { isSinglePublicCertificatePem(submittedPem) }) {
                                    error = "Choose or paste exactly one public PEM certificate."
                                    return@launch
                                }
                                certificateRequest { certificates.importCa(submittedAlias, submittedPem) }
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

/** Keeps certificate file and protocol operations off the Compose UI thread. */
internal suspend fun <T> certificateRequest(operation: () -> T): Result<T> =
    try {
        Result.success(withContext(Dispatchers.IO) { operation() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
