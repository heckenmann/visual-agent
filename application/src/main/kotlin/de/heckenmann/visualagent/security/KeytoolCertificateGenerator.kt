package de.heckenmann.visualagent.security

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Generates constrained self-signed X.509 credentials with the JDK's supported keytool utility. */
internal class KeytoolCertificateGenerator {
    /** Adds one RSA-3072 certificate entry to a temporary PKCS#12 store using fixed keytool options. */
    fun generate(request: CertificateGenerationRequest) {
        require(Files.isRegularFile(request.passwordFile)) { "Managed key-store credential is unavailable." }
        if (request.certificateAuthority) {
            run(command(request, "-genkeypair"))
            return
        }
        checkNotNull(request.signingCaAlias)
        val requestFile = temporaryPublicMaterialFile(request.store, ".csr")
        val certificateFile = temporaryPublicMaterialFile(request.store, ".crt")
        try {
            run(command(request, "-genkeypair"))
            run(command(request, "-certreq", "-file", requestFile.toString()))
            run(
                command(
                    request,
                    "-gencert",
                    "-infile",
                    requestFile.toString(),
                    "-outfile",
                    certificateFile.toString(),
                    "-rfc",
                ),
            )
            run(
                listOf(
                    keytoolExecutable(),
                    "-importcert",
                    "-noprompt",
                    "-alias",
                    request.alias,
                    "-file",
                    certificateFile.toString(),
                    "-keystore",
                    request.store.toString(),
                    "-storetype",
                    "PKCS12",
                    "-storepass:file",
                    request.passwordFile.toString(),
                ),
            )
        } finally {
            Files.deleteIfExists(requestFile)
            Files.deleteIfExists(certificateFile)
        }
    }

    private fun command(
        request: CertificateGenerationRequest,
        operation: String,
        vararg operationArguments: String,
    ): List<String> =
        buildList {
            add(keytoolExecutable())
            add(operation)
            if (operation == "-genkeypair") add("-noprompt")
            add("-alias")
            add(if (operation == "-gencert") checkNotNull(request.signingCaAlias) else request.alias)
            addAll(
                listOf("-keystore", request.store.toString(), "-storetype", "PKCS12", "-storepass:file", request.passwordFile.toString()),
            )
            addAll(listOf("-keypass:file", request.passwordFile.toString()))
            if (operation == "-genkeypair") {
                addAll(
                    listOf(
                        "-dname",
                        request.subject,
                        "-keyalg",
                        "RSA",
                        "-keysize",
                        KEY_SIZE_BITS.toString(),
                        "-sigalg",
                        "SHA384withRSA",
                        "-validity",
                        request.validityDays.toString(),
                    ),
                )
            }
            if (operation == "-gencert") addAll(listOf("-validity", request.validityDays.toString()))
            addAll(operationArguments)
            if (operation == "-genkeypair" || operation == "-gencert") addAll(certificateExtensions(request))
        }

    private fun certificateExtensions(request: CertificateGenerationRequest): List<String> =
        if (request.certificateAuthority) {
            listOf("-ext", "BC=ca:true", "-ext", "KU=keyCertSign,cRLSign")
        } else {
            val names = (request.dnsNames.map { "dns:$it" } + request.ipAddresses.map { "ip:$it" }).joinToString(",")
            listOf("-ext", "BC=ca:false", "-ext", "KU=digitalSignature,keyEncipherment", "-ext", "EKU=serverAuth", "-ext", "SAN=$names")
        }

    private fun run(command: List<String>) {
        val process =
            runCatching {
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
            }.getOrElse { throw IllegalStateException("The JDK keytool utility could not be started.") }
        try {
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()
                throw IllegalStateException("Certificate generation exceeded its execution limit.")
            }
            check(process.exitValue() == 0) { "The JDK keytool utility rejected the certificate request." }
        } catch (interrupted: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw IllegalStateException("Certificate generation was interrupted.")
        }
    }

    private fun temporaryPublicMaterialFile(
        store: Path,
        suffix: String,
    ): Path {
        val file = Files.createTempFile(store.parent, ".certificate-material-", suffix)
        Files.delete(file)
        return file
    }

    private fun keytoolExecutable(): String {
        val executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "keytool.exe" else "keytool"
        val path = Path.of(System.getProperty("java.home"), "bin", executable)
        require(Files.isExecutable(path)) { "The current JDK does not provide an executable keytool utility." }
        return path.toString()
    }

    private companion object {
        const val KEY_SIZE_BITS = 3072
        const val PROCESS_TIMEOUT_SECONDS = 45L
    }
}

/** Validated arguments for one fixed keytool operation. */
internal data class CertificateGenerationRequest(
    /** Already validated store alias. */
    val alias: String,
    /** Canonical X.500 subject name. */
    val subject: String,
    /** Validated DNS SAN values. */
    val dnsNames: List<String>,
    /** Validated numeric IP SAN values. */
    val ipAddresses: List<String>,
    /** Whether to add CA basic constraints and signing key usage. */
    val certificateAuthority: Boolean,
    /** Existing managed CA alias that signs a server certificate, or null for a self-signed CA. */
    val signingCaAlias: String?,
    /** Bounded certificate validity interval. */
    val validityDays: Int,
    /** Managed PKCS#12 file, never model-selected. */
    val store: Path,
    /** Owner-only secret file consumed by keytool's `:file` password option. */
    val passwordFile: Path,
)
