package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ProviderReplayCodec
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.part.OpaquePayload
import org.springframework.ai.chat.messages.part.ReasoningPart
import org.springframework.ai.chat.messages.part.TextPart
import org.springframework.ai.openai.responses.OpenAiResponsesMetadata
import reactor.core.publisher.Mono
import java.nio.file.Path
import kotlin.test.assertEquals

/** Exercises database-to-request replay and cache-friendly reference placement without account access. */
@DatabaseTest
class MainAgentStatelessContextTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `native assistant output survives saving restarting and rebuilding the next request`() =
        runTest {
            val codec = ProviderReplayCodec()
            val native =
                AssistantMessage
                    .builder()
                    .parts(
                        listOf(
                            ReasoningPart(
                                null,
                                null,
                                OpaquePayload("openai.responses", "encrypted_content", "synthetic-reasoning"),
                                mapOf(OpenAiResponsesMetadata.ITEM_ID_ATTRIBUTE to "rs_previous"),
                            ),
                            TextPart.of("First answer"),
                        ),
                    ).build()
            val replay = codec.encode("direct", "selected", native)
            val path = directory.resolve("history.db").toString()
            val first = mockk<LLMProvider>(relaxed = true)
            every { first.chatReactive(any<ChatRequestContext>()) } returns
                Mono.just(ChatResponse("selected", Message("assistant", "First answer", providerReplay = replay), true))
            KnowledgeDbTestFactory.create(path).use { db ->
                val manager = db.createAgentManager(first)
                try {
                    manager.sendMessage("First question")
                    assertEquals(replay, db.getConversationMessages("main").last().providerReplay)
                } finally {
                    manager.destroy()
                }
            }
            val requests = mutableListOf<ChatRequestContext>()
            val next = mockk<LLMProvider>(relaxed = true)
            every { next.chatReactive(capture(requests)) } returns
                Mono.just(ChatResponse("selected", Message("assistant", "Second answer"), true))
            KnowledgeDbTestFactory.create(path).use { db ->
                val manager = db.createAgentManager(next)
                try {
                    manager.sendMessage("Follow-up question")
                    val previous = requests.last().messages.single { it.content == "First answer" }
                    assertEquals(replay, previous.providerReplay)
                    assertEquals(native.parts, codec.restore(requireNotNull(previous.providerReplay), "direct", "selected", native)?.parts)
                    assertEquals(
                        "Follow-up question",
                        requests
                            .last()
                            .messages
                            .last()
                            .content,
                    )
                } finally {
                    manager.destroy()
                }
            }
        }
}
