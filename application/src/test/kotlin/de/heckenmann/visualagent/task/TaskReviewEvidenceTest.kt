package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.orchestration.reviewTaskContract
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A scripted approval cannot promote unsupported, truncated or changed content into inspected evidence. */
@DatabaseTest
class TaskReviewEvidenceTest {
    @Test
    fun `manual review accepts only complete supported text with a matching observed digest`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:review-evidence-${UUID.randomUUID()}").use { db ->
            val root = Files.createTempDirectory("review-evidence")
            try {
                val workspace = WorkspaceFileService(db.workspaceFileStore, root)
                val service = TaskExecutionService(TaskArtifactVerifier(workspace), TaskAttemptStore(db.databaseClient))
                val provider = mockk<LLMProvider>()
                every { provider.chatReactive(any<ChatRequestContext>()) } answers {
                    assertTrue(firstArg<ChatRequestContext>().messages.all { it.images.isNullOrEmpty() })
                    Mono.just(
                        ChatResponse(
                            "scripted",
                            Message(
                                "assistant",
                                """{
                        "verdict":"APPROVED","feedback":"Scripted approval",
                        "criteria":[{"criterionId":"manual","state":"PASSED","detail":"Claimed inspection"}]
                    }""",
                            ),
                            true,
                        ),
                    )
                }
                val text = workspace.writeText("text.txt", "Complete text")
                val binary =
                    workspace.createManagedFile(
                        "test",
                        "pixel.png",
                        Base64.getDecoder().decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS1sAAAAASUVORK5CYII=",
                        ),
                        "image/png",
                    )
                val invalid = workspace.createManagedFile("test", "invalid.txt", byteArrayOf(0xff.toByte()), "text/plain")
                val truncated = workspace.writeText("large.txt", "x".repeat(8_001))
                listOf(text to true, binary to false, invalid to false, truncated to false).forEach { (record, eligible) ->
                    val contract =
                        TaskContract(
                            objective = "Inspect actual content",
                            deliverables = listOf(TaskDeliverable("output", "Output", record.relativePath)),
                            criteria = listOf(TaskCriterion("manual", "Inspect content", "output")),
                        )
                    val todo = Todo(UUID.randomUUID().toString(), "Inspect", contract = contract)
                    val handoff = WorkerHandoff("Done", TaskOutcome.PRODUCED, listOf(TaskArtifact("output", record.relativePath)))
                    val captured =
                        service
                            .capture(
                                todo,
                                "worker",
                                UUID.randomUUID().toString(),
                                1,
                                Json.encodeToString(handoff),
                                emptyList(),
                            ).block()!!
                    val receipt = service.evidence(contract).block()!!
                    assertTrue(receipt.text.length <= 24_000)
                    val reviewed = reviewTaskContract(provider, contract, captured, receipt, null, CancellationToken()).block()!!.second
                    assertEquals(if (eligible) TaskCheckState.PASSED else TaskCheckState.NOT_CHECKED, reviewed.observations.single().state)
                    assertEquals(eligible, service.canAccept(todo, reviewed))
                    if (eligible) {
                        workspace.writeText(record.relativePath, "Changed before review")
                        val changed =
                            reviewTaskContract(
                                provider,
                                contract,
                                captured,
                                service.evidence(contract).block()!!,
                                null,
                                CancellationToken(),
                            ).block()!!.second
                        assertEquals(TaskCheckState.NOT_CHECKED, changed.observations.single().state)
                        assertFalse(service.canAccept(todo, changed))
                    }
                }
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }
}
