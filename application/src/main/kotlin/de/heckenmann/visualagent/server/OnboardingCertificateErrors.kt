package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ProtocolErrorCategory
import de.heckenmann.visualagent.protocol.ProtocolOperationException
import de.heckenmann.visualagent.protocol.UserFacingError
import de.heckenmann.visualagent.security.isMissingTrustAnchor
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Distinguishes an untrusted CA from other HTTPS validation failures without exposing SDK messages. */
internal fun Throwable.onboardingCertificateError(): ProtocolOperationException? {
    val causes = generateSequence(this) { it.cause }.take(16).toList()
    // HTTP clients may report their last address failure as the cause and earlier TLS failures as suppressed.
    val rejection =
        certificateRejection(causes) ?: causes
            .asSequence()
            .flatMap { it.suppressed.asSequence() }
            .take(16)
            .firstNotNullOfOrNull { certificateRejection(generateSequence(it) { failure -> failure.cause }.take(16).toList()) }
            ?: return null
    val detail =
        if (rejection == CertificateRejection.UNTRUSTED_CA) {
            "Review the certificates presented by this provider, or import its public CA certificate, then retry."
        } else {
            "Check that the provider certificate is valid for the hostname in the endpoint URL."
        }
    return ProtocolOperationException(
        UserFacingError(ProtocolErrorCategory.PROVIDER, "Provider TLS verification failed", detail, retryable = true),
        this,
        certificateTrustRequired = rejection == CertificateRejection.UNTRUSTED_CA,
    )
}

/** Gives model discovery an actionable certificate error and preserves other failures. */
internal fun Exception.mapOnboardingDiscoveryFailure(): Exception = onboardingCertificateError() ?: this

private fun certificateRejection(causes: List<Throwable>): CertificateRejection? =
    when {
        causes.any(Throwable::isMissingTrustAnchor) -> CertificateRejection.UNTRUSTED_CA
        causes.any { it is SSLHandshakeException || it is SSLPeerUnverifiedException } -> CertificateRejection.OTHER
        else -> null
    }

private enum class CertificateRejection {
    UNTRUSTED_CA,
    OTHER,
}
