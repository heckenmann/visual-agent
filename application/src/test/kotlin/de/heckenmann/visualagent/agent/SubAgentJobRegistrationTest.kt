package de.heckenmann.visualagent.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Regression tests for registration and terminal cleanup of background sub-agent jobs. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubAgentJobRegistrationTest {
    @Test
    fun `immediate completion does not retain a completed job`() {
        val parent = SupervisorJob()
        val scheduler = scheduler(CoroutineScope(parent + Dispatchers.Unconfined))
        val results = mutableListOf<Pair<String, Result<String>>>()
        try {
            val id = scheduler.enqueue(block = { "done" }, onFinished = { id, result -> results += id to result })

            assertEquals(listOf(id), results.map { it.first })
            assertEquals("done", results.single().second.getOrThrow())
            assertFalse(scheduler.cancelJob(id))
            assertTrue(scheduler.cancelAllJobs().isEmpty())
        } finally {
            scheduler.close()
            parent.cancel()
        }
    }

    @Test
    fun `cancelled parent reports cancellation without executing work`(): Unit =
        runTest {
            val parent = SupervisorJob()
            val scheduler = scheduler(CoroutineScope(parent + StandardTestDispatcher(testScheduler)))
            val results = mutableListOf<Pair<String, Result<String>>>()
            var executed = false
            parent.cancel()
            try {
                val id =
                    scheduler.enqueue(
                        block = {
                            executed = true
                            "unexpected"
                        },
                        onFinished = { id, result -> results += id to result },
                    )
                runCurrent()

                assertFalse(executed)
                assertEquals(listOf(id), results.map { it.first })
                assertIs<CancellationException>(results.single().second.exceptionOrNull())
                assertFalse(scheduler.cancelJob(id))
                assertTrue(scheduler.cancelAllJobs().isEmpty())
                assertEquals(SubAgentJobQueueSnapshot(0, 0), scheduler.snapshot())
            } finally {
                scheduler.close()
                parent.cancel()
            }
        }

    @Test
    fun `immediate failure reports the original exception and removes the job`() {
        val parent = SupervisorJob()
        val scheduler = scheduler(CoroutineScope(parent + Dispatchers.Unconfined))
        val failure = IllegalStateException("Failed work")
        val results = mutableListOf<Result<String>>()
        try {
            val id = scheduler.enqueue<String>(block = { throw failure }, onFinished = { _, result -> results += result })
            assertSame(failure, results.single().exceptionOrNull())
            assertFalse(scheduler.cancelJob(id))
            assertTrue(scheduler.cancelAllJobs().isEmpty())
        } finally {
            scheduler.close()
            parent.cancel()
        }
    }

    @Test
    fun `cancellation before dispatch reports exactly once`(): Unit =
        runTest {
            val parent = SupervisorJob()
            val scheduler = scheduler(CoroutineScope(parent + StandardTestDispatcher(testScheduler)))
            val results = mutableListOf<Result<String>>()
            var executed = false
            try {
                val id =
                    scheduler.enqueue(block = {
                        executed = true
                        "unexpected"
                    }, onFinished = { _, result -> results += result })
                assertTrue(scheduler.cancelJob(id))
                runCurrent()
                assertFalse(executed)
                assertIs<CancellationException>(results.single().exceptionOrNull())
                assertFalse(scheduler.cancelJob(id))
                assertTrue(scheduler.cancelAllJobs().isEmpty())
            } finally {
                scheduler.close()
                parent.cancel()
            }
        }

    @Test
    fun `close cancels running and queued work exactly once`(): Unit =
        runTest {
            val parent = SupervisorJob()
            val scheduler = scheduler(CoroutineScope(parent + StandardTestDispatcher(testScheduler)))
            val results = mutableListOf<Pair<String, Result<String>>>()
            var queuedExecuted = false
            try {
                val running =
                    scheduler.enqueue<String>(
                        block = { awaitCancellation() },
                        onFinished = { id, result -> results += id to result },
                    )
                val queued =
                    scheduler.enqueue(block = {
                        queuedExecuted = true
                        "unexpected"
                    }, onFinished = { id, result ->
                        results +=
                            id to result
                    })
                runCurrent()
                assertEquals(SubAgentJobQueueSnapshot(1, 1), scheduler.snapshot())
                scheduler.close()
                runCurrent()
                assertFalse(queuedExecuted)
                assertEquals(setOf(running, queued), results.map { it.first }.toSet())
                assertEquals(2, results.size)
                results.forEach { assertIs<CancellationException>(it.second.exceptionOrNull()) }
                assertEquals(SubAgentJobQueueSnapshot(0, 0), scheduler.snapshot())
                assertTrue(scheduler.cancelAllJobs().isEmpty())
            } finally {
                scheduler.close()
                parent.cancel()
            }
        }

    @Test
    fun `immediately cancelled parent leaves no completed registration`() {
        val parent = SupervisorJob().also { it.cancel() }
        val scheduler = scheduler(CoroutineScope(parent + Dispatchers.Unconfined))
        val results = mutableListOf<Result<String>>()
        try {
            val id = scheduler.enqueue(block = { "unexpected" }, onFinished = { _, result -> results += result })
            assertIs<CancellationException>(results.single().exceptionOrNull())
            assertFalse(scheduler.cancelJob(id))
            assertTrue(scheduler.cancelAllJobs().isEmpty())
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun `throwing completion callback does not retain the job`() {
        val parent = SupervisorJob()
        val errors = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, error -> errors += error }
        val scheduler = scheduler(CoroutineScope(parent + Dispatchers.Unconfined + handler))
        val failure = IllegalStateException("Failed notification")
        var notifications = 0
        try {
            val id =
                scheduler.enqueue(block = { "done" }, onFinished = { _, _ ->
                    notifications++
                    throw failure
                })
            assertEquals(1, notifications)
            assertSame(failure, errors.single())
            assertFalse(scheduler.cancelJob(id))
            assertTrue(scheduler.cancelAllJobs().isEmpty())
            assertEquals(SubAgentJobQueueSnapshot(0, 0), scheduler.snapshot())
        } finally {
            scheduler.close()
            parent.cancel()
        }
    }

    private fun scheduler(scope: CoroutineScope): SubAgentJobScheduler =
        SubAgentJobScheduler(
            scope,
            object : ParallelismProvider() {
                override fun get(): Int = 1
            },
        )
}
