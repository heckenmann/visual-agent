package de.heckenmann.visualagent.agent

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.Test
import kotlin.test.assertEquals

class LLMProviderDefaultTest {
    @Test
    fun `default chat request overload delegates to message list overload`() {
        val provider = testProvider()

        StepVerifier
            .create(provider.chatReactive(ChatRequestContext(messages = listOf(Message("user", "hi")))))
            .assertNext { assertEquals("ok", it.message.content) }
            .verifyComplete()
    }

    @Test
    fun `default stream request overload delegates to message list overload`() {
        val provider = testProvider()

        StepVerifier
            .create(provider.streamReactive(ChatRequestContext(messages = listOf(Message("user", "hi")))))
            .assertNext { assertEquals("chunk", it.message.content) }
            .verifyComplete()
    }

    @Test
    fun `default getModels with provider id delegates to parameterless overload`() {
        val provider = testProvider()

        assertEquals(listOf("model-a"), provider.getModelsReactive("ignored").block())
    }

    @Test
    fun `default getModelDetails with provider id delegates to single argument overload`() {
        val provider = testProvider()

        assertEquals("now", provider.getModelDetailsReactive("any", "target-model").block()?.modifiedAt)
    }

    private fun testProvider(): LLMProvider =
        object : LLMProvider {
            override fun chatReactive(messages: List<Message>): Mono<ChatResponse> =
                Mono.just(ChatResponse("model", Message("assistant", "ok"), true))

            override fun streamReactive(messages: List<Message>): Flux<ChatResponse> =
                Flux.just(ChatResponse("model", Message("assistant", "chunk"), true))

            override fun visionReactive(
                image: ByteArray,
                prompt: String,
            ): Mono<ChatResponse> = Mono.error(UnsupportedOperationException())

            override fun embeddingsReactive(text: String): Mono<List<Double>> = Mono.just(emptyList())

            override fun isConnected(): Boolean = true

            override fun checkConnectionReactive(): Mono<Boolean> = Mono.just(true)

            override fun getModelsReactive(): Mono<List<String>> = Mono.just(listOf("model-a"))

            override fun getModelDetailsReactive(modelName: String): Mono<ShowResponse> =
                Mono.just(ShowResponse(modelName, "now", details = ModelDetails(family = "test")))
        }
}
