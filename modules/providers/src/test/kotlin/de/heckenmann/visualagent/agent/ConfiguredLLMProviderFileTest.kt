package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeType
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ConfiguredLLMProviderFileTest {
    @Test
    fun `native file routing preserves the explicit profile authentication mode and model`() =
        runTest {
            val profile =
                ProviderProfile(
                    "oauth",
                    "OAuth",
                    ProviderAdapter.CODEX_DIRECT,
                    "https://api.openai.com",
                    defaultModel = "default",
                    directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
                    credentialReference = "ref",
                )
            val catalog = mockk<ProviderCatalogService>()
            val direct = mockk<ProfiledProviderAdapter>()
            every { direct.adapter } returns ProviderAdapter.CODEX_DIRECT
            every { direct.getModelDetailsReactive(profile, "selected") } returns Mono.just(ShowResponse("selected", ""))
            val forwarded = slot<ChatRequestContext>()
            every { direct.fileReactive(any(), capture(forwarded)) } returns
                Mono.just(ChatResponse("selected", Message("assistant", "answer"), true))
            val router = ConfiguredLLMProvider(mockk(), mockk(), catalog, profiledAdapters = listOf(direct))
            val file =
                Media
                    .builder()
                    .mimeType(
                        MimeType.valueOf("application/pdf"),
                    ).data(ByteArrayResource(byteArrayOf(1)))
                    .name("file.pdf")
                    .build()
            val request = ChatRequestContext(listOf(Message("user", "summarize")), model = "selected", providerProfile = profile)

            assertEquals(
                "answer",
                router
                    .fileReactive(file, request)
                    .awaitSingle()
                    .message.content,
            )

            assertSame(profile, forwarded.captured.providerProfile)
            assertEquals("selected", forwarded.captured.model)
            assertEquals(DirectProviderAuthMode.CHATGPT_OAUTH, forwarded.captured.providerProfile?.directAuthMode)
            verify(exactly = 0) { catalog.activeProviderId() }
            verify(exactly = 1) { direct.fileReactive(file, any()) }
        }
}
