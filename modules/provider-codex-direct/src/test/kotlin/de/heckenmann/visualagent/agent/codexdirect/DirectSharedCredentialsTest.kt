package de.heckenmann.visualagent.agent.codexdirect

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import kotlin.test.assertEquals

class DirectSharedCredentialsTest {
    @Test
    fun `SDK transport views retain independent credentials after another view is created`() {
        DirectSseFixture(Flux.just(DirectSseFrames.completed("answer"))).use { fixture ->
            val first = fixture.scopedModel("synthetic-first")
            val second = fixture.scopedModel("synthetic-second")
            val prompt = Prompt(listOf(UserMessage("test")))
            StepVerifier.create(first.stream(prompt)).expectNextCount(1).verifyComplete()
            StepVerifier.create(second.stream(prompt)).expectNextCount(1).verifyComplete()
            StepVerifier.create(first.stream(prompt)).expectNextCount(1).verifyComplete()
            assertEquals(
                listOf("Bearer synthetic-first", "Bearer synthetic-second", "Bearer synthetic-first"),
                fixture.authorizationHeaders,
            )
        }
    }
}
