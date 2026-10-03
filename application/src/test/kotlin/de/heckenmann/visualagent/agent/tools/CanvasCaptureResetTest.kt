package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.canvas.CanvasTool
import de.heckenmann.visualagent.canvas.CanvasImageSnapshot
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies that a capture finishing after reset cannot restore deleted history. */
@DatabaseTest
class CanvasCaptureResetTest {
    @Test
    fun `reset rejects a delayed capture and permits a new immutable snapshot`() =
        runBlocking {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:test").use { db ->
                val captureStarted = CompletableDeferred<Unit>()
                val releaseCapture = CompletableFuture<Unit>()
                val canvas = mockk<CanvasOperations>()
                val snapshot = CanvasImageSnapshot("png", "image/png", byteArrayOf(1, 2, 3), 2, 1)
                every { canvas.captureImage("png") } answers {
                    captureStarted.complete(Unit)
                    releaseCapture.join()
                    snapshot
                }
                val tool = CanvasTool(CanvasToolPortAdapter(canvas, db.conversationStore, mockk<WorkspaceFileService>()))
                val oldRequest = UUID.randomUUID().toString()
                db.beginConversationRequest("main", oldRequest)
                val capture =
                    async(Dispatchers.IO) {
                        tool.execute("""{"action":"captureImage"}""", mapOf("sessionId" to "main", "requestId" to oldRequest))
                    }
                try {
                    captureStarted.await()
                    db.deleteConversationMessages("main")
                    releaseCapture.complete(Unit)

                    assertFalse(capture.await().success)
                    assertEquals(emptyList(), db.getConversationMessages("main"))

                    val newRequest = UUID.randomUUID().toString()
                    db.beginConversationRequest("main", newRequest)
                    assertTrue(
                        tool.execute("""{"action":"captureImage"}""", mapOf("sessionId" to "main", "requestId" to newRequest)).success,
                    )
                    val saved = db.getConversationMessages("main").single()
                    assertEquals(newRequest, saved.conversationRequestId)
                    assertTrue(saved.metadata.orEmpty().contains("\"immutable\":true"))
                } finally {
                    releaseCapture.complete(Unit)
                    capture.join()
                }
            }
        }
}
