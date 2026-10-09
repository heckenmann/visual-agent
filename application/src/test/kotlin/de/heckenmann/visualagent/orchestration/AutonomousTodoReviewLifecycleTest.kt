package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoProgressUpdate
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoTerminalReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies event order at the worker-stream, approval and terminal-transition boundaries. */
class AutonomousTodoReviewLifecycleTest {
    @Test
    fun `finished worker enters reviewing before a gated approval and completes with its feedback`(): Unit =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val fixture =
                buildFixture(reviewContent = """{"verdict":"APPROVED","feedback":"The work is ready."}""", reviewResponseGate = gate)
            fixture.putSubAgent(SubAgent("worker", "Worker", "Implementation"))
            val todo = fixture.todoManager.add("Write a short result", "worker")
            val progress = CopyOnWriteArrayList<TodoProgressUpdate>()
            val changes = CopyOnWriteArrayList<TodoChange>()
            val approvedChange = CompletableDeferred<TodoChange>()
            val progressHandle = fixture.todoEventBus.addProgressListener(progress::add)
            val changeHandle =
                fixture.todoEventBus.addListener { change ->
                    changes.add(change)
                    if (change.approval != null) approvedChange.complete(change)
                }
            try {
                fixture.coordinator.startTodo(todo.id)
                fixture.awaitReviewStart()
                assertEquals(TodoStatus.IN_PROGRESS, fixture.todoManager.getById(todo.id)?.status)
                assertTrue(progress.last().reviewing)
                assertTrue(progress.last().completed)
                assertFalse(progress.first().reviewing)
                assertFalse(changes.any { it.approval != null })

                gate.complete(Unit)
                val completed = approvedChange.await()
                assertEquals(TodoStatus.COMPLETED, completed.todo?.status)
                assertEquals("The work is ready.", completed.approval?.feedback)
                assertEquals(fixture.messages.first().conversationRequestId, completed.approval?.conversationRequestId)
                assertEquals(1, fixture.providerRequests.count { it.metadata["sessionId"] == "review" })
            } finally {
                gate.complete(Unit)
                progressHandle.close()
                changeHandle.close()
                fixture.cancel()
            }
        }

    @Test
    fun `malformed approval retries only the review and never rejects or reruns the worker`(): Unit =
        runBlocking {
            val fixture = buildFixture(reviewContent = "APPROVED_BUT_NOT_VALID")
            fixture.putSubAgent(SubAgent("worker", "Worker", "Implementation"))
            val todo = fixture.todoManager.add("Write a short result", "worker")
            val changes = CopyOnWriteArrayList<TodoChange>()
            val cancelledChange = CompletableDeferred<TodoChange>()
            val handle =
                fixture.todoEventBus.addListener { change ->
                    changes += change
                    if (change.todo?.id == todo.id && change.todo.status == TodoStatus.CANCELLED) cancelledChange.complete(change)
                }
            try {
                fixture.coordinator.startTodo(todo.id)
                val cancelled = cancelledChange.await()
                assertFalse(changes.any { it.approval != null })
                assertEquals(TodoTerminalReason.REVIEW_FAILED, cancelled.terminalReason)
                assertFalse(fixture.messages.any { it.content.contains("Main review rejected") })
                assertEquals(2, fixture.providerRequests.count { it.metadata["sessionId"] == "review" })
                assertEquals(1, fixture.providerRequests.count { it.metadata["sessionId"] != "review" })
            } finally {
                handle.close()
                fixture.cancel()
            }
        }

    @Test
    fun `stopping a todo during review prevents late approval`(): Unit =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val fixture = buildFixture(reviewContent = """{"verdict":"APPROVED","feedback":"Late feedback"}""", reviewResponseGate = gate)
            fixture.putSubAgent(SubAgent("worker", "Worker", "Implementation"))
            val todo = fixture.todoManager.add("Write a short result", "worker")
            val changes = CopyOnWriteArrayList<TodoChange>()
            val handle = fixture.todoEventBus.addListener(changes::add)
            try {
                fixture.coordinator.startTodo(todo.id)
                fixture.awaitReviewStart()
                assertTrue(fixture.coordinator.stopTodo(todo.id))
                gate.complete(Unit)
                fixture.awaitTodoStatus(todo.id, TodoStatus.CANCELLED)
                assertFalse(changes.any { it.approval != null })
            } finally {
                gate.complete(Unit)
                handle.close()
                fixture.cancel()
            }
        }

    @Test
    fun `review parser retains feedback and rejects ambiguous verdicts`() {
        assertEquals(
            WorkerReviewResult(WorkerReviewVerdict.APPROVED, "Ready."),
            WorkerReviewResult.parse(""" {"verdict":"APPROVED","feedback":"Ready."} """),
        )
        assertEquals(
            WorkerReviewResult(WorkerReviewVerdict.RETRY, "Missing output."),
            WorkerReviewResult.parse("""{"verdict":"RETRY","feedback":"Missing output."}"""),
        )
        listOf(
            "",
            "APPROVED",
            "APPROVED_BUT",
            "The result is APPROVED",
            """{"verdict":"approved","feedback":"Ready"}""",
            """{"verdict":"APPROVED"}""",
            """{"verdict":"APPROVED","feedback":""}""",
            """{"verdict":"APPROVED","feedback":null}""",
            """{"verdict":"APPROVED","feedback":3}""",
            """{"verdict":"APPROVED","feedback":"Ready","extra":true}""",
            """{"verdict":"RETRY","feedback":"Missing"} trailing prose""",
        ).forEach {
            assertFailsWith<WorkerReviewFormatException> { WorkerReviewResult.parse(it) }
        }
    }

    @Test
    fun `corrected review approves the same worker execution`(): Unit =
        runBlocking {
            val fixture = buildFixture(reviewResponses = listOf("invalid", """{"verdict":"APPROVED","feedback":"Ready."}"""))
            fixture.putSubAgent(SubAgent("worker", "Worker", "Implementation"))
            val todo = fixture.todoManager.add("Write a short result", "worker")
            try {
                fixture.coordinator.startTodo(todo.id)
                fixture.awaitTodoStatus(todo.id, TodoStatus.COMPLETED)
                assertEquals(1, fixture.providerRequests.count { it.metadata["sessionId"] != "review" })
                val reviews = fixture.providerRequests.filter { it.metadata["sessionId"] == "review" }
                assertEquals(2, reviews.size)
                assertEquals(reviews.first().messages.last(), reviews.last().messages.last())
                assertEquals(WorkerReviewResult.schema(), reviews.last().responseSchema)
                assertTrue(
                    reviews
                        .last()
                        .messages
                        .first()
                        .content
                        .contains("invalid format"),
                )
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `valid rejection still retries worker work`(): Unit =
        runBlocking {
            val fixture =
                buildFixture(
                    reviewResponses =
                        listOf(
                            """{"verdict":"RETRY","feedback":"Missing output."}""",
                            """{"verdict":"APPROVED","feedback":"Ready."}""",
                        ),
                )
            fixture.putSubAgent(SubAgent("worker", "Worker", "Implementation"))
            val todo = fixture.todoManager.add("Write a short result", "worker")
            try {
                fixture.coordinator.startTodo(todo.id)
                fixture.awaitTodoStatus(todo.id, TodoStatus.COMPLETED)
                assertEquals(2, fixture.providerRequests.count { it.metadata["sessionId"] != "review" })
                assertEquals(2, fixture.providerRequests.count { it.metadata["sessionId"] == "review" })
                assertTrue(fixture.messages.any { it.content.contains("Main review rejected") })
                assertTrue(
                    fixture.providerRequests
                        .filter { it.metadata["sessionId"] != "review" }
                        .last()
                        .messages
                        .any { it.content.contains("Review correction:") },
                )
            } finally {
                fixture.cancel()
            }
        }

    @Test
    fun `provider failure during review never repeats the worker`(): Unit =
        runBlocking {
            val fixture = buildFixture(reviewFailure = IllegalStateException("provider unavailable"))
            fixture.putSubAgent(SubAgent("worker", "Worker", "Implementation"))
            val todo = fixture.todoManager.add("Write a short result", "worker")
            val terminal = CompletableDeferred<TodoChange>()
            val handle = fixture.todoEventBus.addListener { if (it.todo?.status == TodoStatus.CANCELLED) terminal.complete(it) }
            try {
                fixture.coordinator.startTodo(todo.id)
                assertEquals(TodoTerminalReason.REVIEW_FAILED, terminal.await().terminalReason)
                assertEquals(1, fixture.providerRequests.count { it.metadata["sessionId"] != "review" })
                assertEquals(1, fixture.providerRequests.count { it.metadata["sessionId"] == "review" })
            } finally {
                handle.close()
                fixture.cancel()
            }
        }
}
