package de.heckenmann.visualagent.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubAgentJobSchedulerTest {
    private fun scheduler(parallelism: Int = 4): SubAgentJobScheduler {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val provider =
            object : ParallelismProvider() {
                override fun get(): Int = parallelism
            }
        return SubAgentJobScheduler(scope, provider)
    }

    @Test
    fun `synchronous job waits until capacity is available`() =
        runBlocking {
            val scheduler = scheduler(1)
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()

            val first =
                async {
                    scheduler.run {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                        "first"
                    }
                }
            firstStarted.await()

            val second =
                async {
                    scheduler.run {
                        secondStarted.complete(Unit)
                        "second"
                    }
                }

            delay(150)
            assertFalse(secondStarted.isCompleted)
            assertEquals(SubAgentJobQueueSnapshot(active = 1, queued = 1), scheduler.snapshot())

            releaseFirst.complete(Unit)
            assertEquals("first", first.await())
            assertEquals("second", second.await())
        }

    @Test
    fun `asynchronous job is queued and completion callback is invoked`() =
        runBlocking {
            val scheduler = scheduler(1)
            val releaseFirst = CompletableDeferred<Unit>()
            val firstStarted = CompletableDeferred<Unit>()
            val completion = CompletableDeferred<Result<String>>()

            val first =
                async {
                    scheduler.run {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                }
            firstStarted.await()

            val jobId =
                scheduler.enqueue(
                    block = { "queued-result" },
                    onFinished = { _, result -> completion.complete(result) },
                )

            assertTrue(jobId.isNotBlank())
            delay(150)
            assertFalse(completion.isCompleted)
            assertEquals(1, scheduler.snapshot().queued)

            releaseFirst.complete(Unit)
            first.await()
            assertEquals("queued-result", completion.await().getOrThrow())
        }
}
