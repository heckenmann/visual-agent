package de.heckenmann.visualagent.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubAgentJobSchedulerTest {
    private fun scheduler(parallelism: Int = 4): SubAgentJobScheduler {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
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
                async(start = CoroutineStart.UNDISPATCHED) {
                    scheduler.run {
                        secondStarted.complete(Unit)
                        "second"
                    }
                }

            assertEquals(SubAgentJobQueueSnapshot(active = 1, queued = 1), scheduler.snapshot())

            releaseFirst.complete(Unit)
            assertEquals("first", first.await())
            secondStarted.await()
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
            assertEquals(1, scheduler.snapshot().queued)

            releaseFirst.complete(Unit)
            first.await()
            assertEquals("queued-result", completion.await().getOrThrow())
        }

    @Test
    fun `paused jobs remain queued until their gate resumes`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val provider =
                object : ParallelismProvider() {
                    override fun get(): Int = 1
                }
            val control = SubAgentExecutionControl(TestPreferenceStore())
            control.pauseAll()
            val scheduler = SubAgentJobScheduler(scope, provider, control)
            val started = CompletableDeferred<Unit>()
            val job =
                async(start = CoroutineStart.UNDISPATCHED) {
                    scheduler.run("agent-1") {
                        started.complete(Unit)
                    }
                }

            assertEquals(SubAgentJobQueueSnapshot(active = 0, queued = 1), scheduler.snapshot())
            control.resumeAll()
            started.await()
            job.join()
            assertEquals(SubAgentJobQueueSnapshot(active = 0, queued = 0), scheduler.snapshot())
            scope.coroutineContext[Job]?.cancel()
        }

    @Test
    fun `close cancels active jobs and releases scheduler subscriptions`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val scheduler =
                SubAgentJobScheduler(
                    scope,
                    object : ParallelismProvider() {
                        override fun get(): Int = 1
                    },
                )
            val started = CompletableDeferred<Unit>()
            val job =
                async {
                    scheduler.run {
                        started.complete(Unit)
                        awaitCancellation()
                    }
                }

            started.await()
            scheduler.close()

            job.join()
            assertEquals(SubAgentJobQueueSnapshot(active = 0, queued = 0), scheduler.snapshot())
            scope.coroutineContext[Job]?.cancel()
        }

    private class TestPreferenceStore : de.heckenmann.visualagent.knowledge.PreferenceStore {
        private val values = mutableMapOf<String, String>()

        override fun getPreference(key: String): String? = values[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }
}
