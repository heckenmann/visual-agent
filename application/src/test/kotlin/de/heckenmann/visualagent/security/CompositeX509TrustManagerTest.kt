package de.heckenmann.visualagent.security

import io.mockk.every
import io.mockk.mockk
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** Verifies additive trust-manager issuer reporting. */
class CompositeX509TrustManagerTest {
    @Test
    fun `retains distinct certificates with the same subject and removes exact duplicates`() {
        val subject = X500Principal("CN=Shared Subject")
        val platformCertificate = mockk<X509Certificate>()
        val managedCertificate = mockk<X509Certificate>()
        every { platformCertificate.subjectX500Principal } returns subject
        every { managedCertificate.subjectX500Principal } returns subject
        val platform = mockk<X509TrustManager>()
        val managed = mockk<X509TrustManager>()
        every { platform.acceptedIssuers } returns arrayOf(platformCertificate)
        every { managed.acceptedIssuers } returns arrayOf(managedCertificate, platformCertificate)

        val issuers = CompositeX509TrustManager(platform, managed).acceptedIssuers

        assertContentEquals(arrayOf(platformCertificate, managedCertificate), issuers)
    }
}
