package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.tools.ServerTlsEntry
import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import de.heckenmann.visualagent.protocol.CertificateTrustPort
import de.heckenmann.visualagent.protocol.PresentedCertificateChain
import de.heckenmann.visualagent.protocol.ProtocolErrorCategory
import de.heckenmann.visualagent.protocol.ProtocolOperationException
import de.heckenmann.visualagent.protocol.TrustedCertificate
import de.heckenmann.visualagent.protocol.UserFacingError
import de.heckenmann.visualagent.security.ManagedTlsMaterialService
import de.heckenmann.visualagent.security.PresentedTlsCertificates
import de.heckenmann.visualagent.security.TlsEndpoint
import de.heckenmann.visualagent.security.isEndpointCertificateAlias
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URISyntaxException
import java.security.GeneralSecurityException
import java.security.cert.CertificateException

/** Exposes public certificate trust operations to administrative UI. */
@Component
class SpringCertificateTrustPort internal constructor(
    private val material: ManagedTlsMaterialService,
    private val presented: PresentedTlsCertificates,
) : CertificateTrustPort {
    override fun list(): List<TrustedCertificate> =
        protocolBoundary { (material.list(ServerTlsStore.TRUST) + material.endpointCertificates()).map { it.toProtocol() } }

    override fun importCa(
        alias: String,
        pem: String,
    ): TrustedCertificate =
        protocolBoundary {
            try {
                material.importTrustedCertificate(alias.trim(), pem).toProtocol()
            } catch (error: IllegalArgumentException) {
                throw rejectedImport(error)
            } catch (error: CertificateException) {
                throw rejectedImport(error)
            }
        }

    override fun exportCertificate(alias: String): String? =
        protocolBoundary {
            if (alias.isEndpointCertificateAlias()) {
                material.exportEndpointCertificate(alias)
            } else {
                material
                    .inspect(ServerTlsStore.TRUST, alias, includePem = true)
                    ?.certificates
                    ?.single()
                    ?.pem
            }
        }

    override fun removeCertificate(
        alias: String,
        sha256: String,
    ): Boolean =
        protocolBoundary {
            if (alias.isEndpointCertificateAlias()) {
                material.removeEndpointCertificate(alias, sha256)
            } else {
                material.removeTrustedCertificate(alias, sha256)
            }
        }

    override fun reviewEndpoint(baseUrl: String): PresentedCertificateChain =
        endpointOperation("Could not review server certificates", "Check the HTTPS address and try again.") {
            presented.review(TlsEndpoint.parse(baseUrl))
        }

    override fun importEndpointCertificates(
        baseUrl: String,
        reviewedFingerprints: List<String>,
        selectedFingerprints: Set<String>,
    ) {
        val detail = "Check the HTTPS address and certificate validity, then reload the chain and verify its fingerprints before retrying."
        endpointOperation("Could not import the reviewed certificates", detail) {
            val endpoint = TlsEndpoint.parse(baseUrl)
            material.importReviewedCertificates(endpoint, presented.confirm(endpoint, reviewedFingerprints, selectedFingerprints))
        }
    }

    private fun <T> endpointOperation(
        summary: String,
        detail: String,
        action: () -> T,
    ): T =
        protocolBoundary {
            try {
                action()
            } catch (error: Exception) {
                if (error !is IOException &&
                    error !is GeneralSecurityException &&
                    error !is URISyntaxException &&
                    error !is IllegalArgumentException
                ) {
                    throw error
                }
                throw ProtocolOperationException(UserFacingError(ProtocolErrorCategory.PROVIDER, summary, detail, retryable = true), error)
            }
        }

    private fun ServerTlsEntry.toProtocol(): TrustedCertificate =
        certificates.single().let {
            val endpoint = alias.takeIf(String::isEndpointCertificateAlias)?.removePrefix(TlsEndpoint.ENDPOINT_ALIAS_PREFIX)
            TrustedCertificate(alias, it.subject, it.issuer, it.notBefore, it.notAfter, it.sha256, endpoint)
        }

    private fun rejectedImport(cause: Exception): ProtocolOperationException =
        ProtocolOperationException(
            UserFacingError(
                ProtocolErrorCategory.PERSISTENCE,
                "CA certificate import rejected",
                "Use a unique alias and one currently valid public X.509 CA certificate in PEM format.",
            ),
            cause,
        )
}
