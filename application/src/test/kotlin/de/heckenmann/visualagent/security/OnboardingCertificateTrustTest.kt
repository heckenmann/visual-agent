package de.heckenmann.visualagent.security

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import de.heckenmann.visualagent.agent.ConfiguredLLMProvider
import de.heckenmann.visualagent.agent.OllamaClient
import de.heckenmann.visualagent.agent.ollama.OllamaApiConfiguration
import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import de.heckenmann.visualagent.agent.ollama.OllamaToolRecovery
import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.openai.OpenAiPromptFactory
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.CredentialUpdate
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingValidationCode
import de.heckenmann.visualagent.protocol.PresentedCertificateKind
import de.heckenmann.visualagent.protocol.ProtocolOperationException
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.server.SpringCertificateTrustPort
import de.heckenmann.visualagent.server.SpringOnboardingPort
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests trust administration and real provider onboarding transport with separate server and endpoint roots. */
class OnboardingCertificateTrustTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @CsvSource("OPENAI_COMPATIBLE,SERVER", "OLLAMA,SERVER", "OPENAI_COMPATIBLE,ROOT_CA", "OLLAMA,ROOT_CA")
    fun `an HTTPS provider can complete onboarding after reviewed import and still enforces hostname verification`(
        adapter: ProviderAdapter,
        choice: PresentedCertificateKind,
    ) = runTest {
        val trust = material("application")
        val certificates = SpringCertificateTrustPort(trust, PresentedTlsCertificates())
        val endpoint = endpointMaterial()
        val requests = AtomicInteger()
        val server = httpsApi(endpoint, requests)
        try {
            KnowledgeDbTestFactory.create(directory.resolve("db").toString()).use { db ->
                val catalog = ProviderCatalogService(db.preferenceStore)
                val provider = configuredProvider(catalog, trust)
                val onboarding = SpringOnboardingPort(db.preferenceStore, catalog, provider, mockk())
                val path = if (adapter == ProviderAdapter.OPENAI_COMPATIBLE) "/v1" else ""
                val draft =
                    OnboardingProviderDraft(
                        id = "private-api",
                        name = "Private API",
                        adapter = adapter,
                        baseUrl = "https://localhost:${server.address.port}$path",
                        credential = CredentialUpdate.Replace("test-only-key"),
                    )

                val discoveryError = assertFailsWith<ProtocolOperationException> { onboarding.discoverModels(draft) }
                assertTrue(discoveryError.certificateTrustRequired)
                val initialValidation = onboarding.validate(draft, MODEL)
                assertEquals(OnboardingValidationCode.TLS_VERIFICATION_FAILED, initialValidation.code)
                assertTrue(initialValidation.certificateTrustRequired)
                assertEquals(0, requests.get())
                assertTrue(onboarding.providers().isEmpty())

                val chain = certificates.reviewEndpoint(draft.baseUrl)
                assertEquals(
                    listOf(PresentedCertificateKind.SERVER, PresentedCertificateKind.ROOT_CA),
                    chain.certificates.map { it.kind },
                )
                val fingerprints = chain.certificates.map { it.certificate.sha256 }
                val approved =
                    chain.certificates
                        .single { it.kind == choice }
                        .certificate.sha256
                certificates.importEndpointCertificates(draft.baseUrl, fingerprints, setOf(approved))
                assertEquals(0, requests.get())
                val model = onboarding.discoverModels(draft).single()
                val validation = onboarding.validate(draft, model.id)
                assertEquals(MODEL, model.id)
                assertTrue(validation.success, validation.message)
                onboarding.finish(draft, model, assertNotNull(validation.validationFingerprint))
                assertEquals(draft.baseUrl, onboarding.providers().single().baseUrl)

                val previousRequests = requests.get()
                val mismatch = draft.copy(baseUrl = "https://127.0.0.1:${server.address.port}$path")
                val rejected = onboarding.validate(mismatch, MODEL)
                assertEquals(OnboardingValidationCode.TLS_VERIFICATION_FAILED, rejected.code)
                if (choice == PresentedCertificateKind.ROOT_CA) {
                    assertContains(rejected.message, "hostname")
                    assertFalse(rejected.certificateTrustRequired)
                }
                assertEquals(previousRequests, requests.get())
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `public trust entries survive service reopen and removal verifies the confirmed fingerprint`() {
        val endpoint = endpointMaterial()
        val application = material("application")
        val defaultRoots = application.trustManager().acceptedIssuers.toSet()
        val store = SpringCertificateTrustPort(application, PresentedTlsCertificates())
        val pem = assertNotNull(endpoint.exportPublicCertificate("endpoint-ca"))
        val leaf = assertNotNull(endpoint.exportPublicCertificate("endpoint"))
        application.generateCertificate("identity", "CN=Identity CA", emptyList(), emptyList(), true, 30)
        assertNull(store.exportCertificate("identity"))
        assertFailsWith<ProtocolOperationException> { store.importCa("leaf", leaf) }
        val keyMarker = "-----BEGIN PRIVATE KEY-----\nZmFrZQ==\n-----END PRIVATE KEY-----\n"
        assertFailsWith<ProtocolOperationException> { store.importCa("key", "$pem$keyMarker") }
        assertFailsWith<ProtocolOperationException> { store.importCa("multiple", "$pem$pem") }
        assertTrue(store.list().isEmpty())

        val imported = store.importCa("organization", pem)
        val restartedMaterial = material("application")
        val restarted = SpringCertificateTrustPort(restartedMaterial, PresentedTlsCertificates())
        assertTrue(
            restartedMaterial
                .trustManager()
                .acceptedIssuers
                .toSet()
                .containsAll(defaultRoots),
        )
        assertEquals(listOf(imported), restarted.list())
        assertEquals("CN=Onboarding Test CA", imported.subject)
        assertEquals(imported.subject, imported.issuer)
        assertTrue(imported.notBefore < imported.notAfter)
        assertEquals(64, imported.sha256.length)
        assertEquals(pem, restarted.exportCertificate(imported.alias))
        assertFalse(restarted.removeCertificate(imported.alias, "0".repeat(64)))
        assertEquals(listOf(imported), restarted.list())
        assertTrue(restarted.removeCertificate(imported.alias, imported.sha256))
        assertTrue(SpringCertificateTrustPort(material("application"), PresentedTlsCertificates()).list().isEmpty())
    }

    private fun material(name: String): ManagedTlsMaterialService {
        val files = ManagedTlsStoreFiles(directory.resolve(name))
        val audit = TlsMutationAudit()
        return ManagedTlsMaterialService(files, audit, ManagedTlsCertificateOperations(files, audit))
    }

    private fun endpointMaterial(): ManagedTlsMaterialService =
        material("endpoint").apply {
            generateCertificate("endpoint-ca", "CN=Onboarding Test CA", emptyList(), emptyList(), true, 30)
            generateCertificate("endpoint", "CN=localhost", listOf("localhost"), emptyList(), false, 30, "endpoint-ca")
        }

    private fun configuredProvider(
        catalog: ProviderCatalogService,
        trust: ManagedTlsMaterialService,
    ): ConfiguredLLMProvider {
        val callbacks = mockk<ProviderToolCallbacks>(relaxed = true)
        val openAi = OpenAiClient(OpenAiPromptFactory(callbacks), callbacks, serverTrustManagerProvider = trust)
        val config = AppConfigBean()
        val beans = OllamaApiConfiguration(config, trust)
        val ollamaApi = beans.ollamaApi()
        val chatModel = beans.ollamaChatModel(ollamaApi)
        val prompts = OllamaPromptFactory(callbacks)
        val ollama = OllamaClient(chatModel, ollamaApi, prompts, OllamaToolRecovery(chatModel, prompts), callbacks, config, trust)
        return ConfiguredLLMProvider(ollama, openAi, catalog)
    }

    private fun httpsApi(
        material: ManagedTlsMaterialService,
        requests: AtomicInteger,
    ): HttpsServer {
        val tls = SSLContext.getInstance("TLS").apply { init(material.keyManagerFactory("endpoint").keyManagers, null, null) }
        return HttpsServer.create(InetSocketAddress("localhost", 0), 0).apply {
            httpsConfigurator = HttpsConfigurator(tls)
            createContext("/") { exchange ->
                requests.incrementAndGet()
                val payload =
                    when (exchange.requestURI.path) {
                        "/v1/models" -> """{"object":"list","data":[{"id":"$MODEL","object":"model","created":1,"owned_by":"test"}]}"""
                        "/v1/chat/completions" ->
                            """
                            {"id":"probe","object":"chat.completion","created":1,"model":"$MODEL","choices":[{"index":0,
                            "message":{"role":"assistant","content":"READY"},"finish_reason":"stop"}]}
                            """.trimIndent()
                        "/api/tags" -> """{"models":[{"name":"$MODEL","model":"$MODEL"}]}"""
                        "/api/show" ->
                            """{"details":{"family":"test"},"capabilities":["completion"],"model_info":{"test.context_length":4096}}"""
                        "/api/chat" ->
                            """
                            {"model":"$MODEL","created_at":"2026-01-01T00:00:00Z","message":{"role":"assistant","content":"READY"},
                            "done":true,"done_reason":"stop","prompt_eval_count":1,"eval_count":1}
                            """.trimIndent()
                        else -> null
                    }
                if (payload == null) {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                } else {
                    val bytes = payload.toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            }
            start()
        }
    }

    private companion object {
        const val MODEL = "gpt-test"
    }
}
