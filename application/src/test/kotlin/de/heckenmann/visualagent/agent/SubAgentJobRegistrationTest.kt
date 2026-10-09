package de.heckenmann.visualagent.agent

import reactor.core.publisher.Mono
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Native regression tests for registration and terminal cleanup of background sub-agent jobs. */
class SubAgentJobRegistrationTest {
    @Test
    fun `immediate completion does not retain a completed job`() {
        scheduler().use { scheduler ->
            val finished = CountDownLatch(1)
            val results = CopyOnWriteArrayList<Pair<String, Result<String>>>()
            val id =
                scheduler.enqueueReactive(block = { Mono.just("done") }, onFinished = { id, result ->
                    results += id to result
                    finished.countDown()
                })
            await(finished)
            assertEquals(listOf(id), results.map { it.first })
            assertEquals("done", results.single().second.getOrThrow())
            assertFalse(scheduler.cancelJob(id))
            assertTrue(scheduler.cancelAllJobs().isEmpty())
        }
    }

    @Test
    fun `closed scheduler reports cancellation without executing work`() {
        val scheduler = scheduler()
        val finished = CountDownLatch(1)
        val results = CopyOnWriteArrayList<Result<String>>()
        var executed = false
        scheduler.close()
        val id =
            scheduler.enqueueReactive(block = {
                executed = true
                Mono.just("unexpected")
            }, onFinished = { _, result ->
                results += result
                finished.countDown()
            })
        await(finished)
        assertFalse(executed)
        assertIs<CancellationException>(results.single().exceptionOrNull())
        assertFalse(scheduler.cancelJob(id))
        assertEquals(SubAgentJobQueueSnapshot(0, 0), scheduler.snapshot())
    }

    @Test
    fun `immediate failure reports the original exception and removes the job`() {
        scheduler().use { scheduler ->
            val failure = IllegalStateException("Failed work")
            val finished = CountDownLatch(1)
            val results = CopyOnWriteArrayList<Result<String>>()
            val id =
                scheduler.enqueueReactive(block = { Mono.error<String>(failure) }, onFinished = { _, result ->
                    results += result
                    finished.countDown()
                })
            await(finished)
            assertSame(failure, results.single().exceptionOrNull())
            assertFalse(scheduler.cancelJob(id))
            assertTrue(scheduler.cancelAllJobs().isEmpty())
        }
    }

    @Test
    fun `cancellation before dispatch reports exactly once`() {
        scheduler().use { scheduler ->
            val running = scheduler.runReactive("first", "first") { Mono.never<String>() }.subscribe()
            val finished = CountDownLatch(1)
            val results = CopyOnWriteArrayList<Result<String>>()
            var executed = false
            val id =
                scheduler.enqueueReactive(block = {
                    executed = true
                    Mono.just("unexpected")
                }, onFinished = { _, result ->
                    results += result
                    finished.countDown()
                })
            assertEquals(SubAgentJobQueueSnapshot(1, 1), scheduler.snapshot())
            assertTrue(scheduler.cancelJob(id))
            await(finished)
            assertFalse(executed)
            assertIs<CancellationException>(results.single().exceptionOrNull())
            assertFalse(scheduler.cancelJob(id))
            assertTrue(scheduler.cancelAllJobs().isEmpty())
            running.dispose()
        }
    }

    @Test
    fun `close cancels running and queued work exactly once`() {
        val scheduler = scheduler()
        val started = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val results = CopyOnWriteArrayList<Pair<String, Result<String>>>()
        val running =
            scheduler.enqueueReactive(block = {
                started.countDown()
                Mono.never<String>()
            }, onFinished = { id, result ->
                results += id to result
                finished.countDown()
            })
        await(started)
        var queuedExecuted = false
        val queued =
            scheduler.enqueueReactive(block = {
                queuedExecuted = true
                Mono.just("unexpected")
            }, onFinished = { id, result ->
                results += id to result
                finished.countDown()
            })
        assertEquals(SubAgentJobQueueSnapshot(1, 1), scheduler.snapshot())
        scheduler.close()
        await(finished)
        assertFalse(queuedExecuted)
        assertEquals(setOf(running, queued), results.map { it.first }.toSet())
        assertEquals(2, results.size)
        results.forEach { assertIs<CancellationException>(it.second.exceptionOrNull()) }
        assertEquals(SubAgentJobQueueSnapshot(0, 0), scheduler.snapshot())
        assertTrue(scheduler.cancelAllJobs().isEmpty())
    }

    @Test
    fun `repeated close leaves no completed registration`() {
        val scheduler = scheduler()
        scheduler.close()
        scheduler.close()
        val finished = CountDownLatch(1)
        val id =
            scheduler.enqueueReactive(block = { Mono.just("unexpected") }, onFinished = { _, result ->
                assertIs<CancellationException>(result.exceptionOrNull())
                finished.countDown()
            })
        await(finished)
        assertFalse(scheduler.cancelJob(id))
        assertTrue(scheduler.cancelAllJobs().isEmpty())
    }

    @Test
    fun `throwing completion callback does not retain the job`() {
        scheduler().use { scheduler ->
            val finished = CountDownLatch(1)
            val id =
                scheduler.enqueueReactive(block = { Mono.just("done") }, onFinished = { _, _ ->
                    finished.countDown()
                    throw IllegalStateException("Failed notification")
                })
            await(finished)
            assertFalse(scheduler.cancelJob(id))
            assertTrue(scheduler.cancelAllJobs().isEmpty())
            assertEquals(SubAgentJobQueueSnapshot(0, 0), scheduler.snapshot())
        }
    }

    private fun await(signal: CountDownLatch) {
        assertTrue(signal.await(5, TimeUnit.SECONDS), "Expected lifecycle event did not arrive")
    }

    private fun scheduler(): SubAgentJobScheduler =
        SubAgentJobScheduler(
            object : ParallelismProvider() {
                override fun get(): Int = 1
            },
        )
}
