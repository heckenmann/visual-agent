package de.heckenmann.visualagent.agent

import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises native scheduling and cancellation without a coroutine bridge. */
class ReactorSchedulerTest {
    @Test
    fun `native execution gate waits for both persisted pause flags`() {
        val values = mutableMapOf<String, String>()
        val store =
            object : de.heckenmann.visualagent.knowledge.PreferenceStore {
                override fun getPreference(key: String): String? = values[key]

                override fun setPreference(
                    key: String,
                    value: String,
                ) {
                    values[key] = value
                }
            }
        val control = SubAgentExecutionControl(store)
        control.pauseAll()
        control.pauseAgent("worker")
        StepVerifier
            .create(control.executionAllowed("worker"))
            .then { control.resumeAll() }
            .then { kotlin.test.assertTrue(control.isAgentPaused("worker")) }
            .then { control.resumeAgent("worker") }
            .verifyComplete()
    }

    @Test
    fun `cancelling queued subscriber releases its request without consuming capacity`() {
        val provider =
            object : ParallelismProvider() {
                override fun get(): Int = 1
            }
        SubAgentJobScheduler(provider).use { scheduler ->
            val first = TestPublisher.create<String>()
            val running = scheduler.runReactive("first", "first") { first.mono() }.subscribe()
            StepVerifier
                .create(scheduler.runReactive("second", "second") { Mono.just("second") })
                .then { assertEquals(SubAgentJobQueueSnapshot(1, 1), scheduler.snapshot()) }
                .thenCancel()
                .verify()
            assertEquals(SubAgentJobQueueSnapshot(1, 0), scheduler.snapshot())
            running.dispose()
            first.assertCancelled()
            assertEquals(SubAgentJobQueueSnapshot(0, 0), scheduler.snapshot())
        }
    }

    @Test
    fun `native queued request cancellation delivers an error and leaves the active job alone`() {
        val provider =
            object : ParallelismProvider() {
                override fun get(): Int = 1
            }
        SubAgentJobScheduler(provider).use { scheduler ->
            val first = TestPublisher.create<String>()
            val running = scheduler.runReactive("first", "first") { first.mono() }.subscribe()
            StepVerifier
                .create(scheduler.runReactive("second", "second") { Mono.just("second") })
                .then { assertEquals(1, scheduler.cancelQueuedRequest("second")) }
                .expectError(java.util.concurrent.CancellationException::class.java)
                .verify()
            assertEquals(SubAgentJobQueueSnapshot(1, 0), scheduler.snapshot())
            running.dispose()
        }
    }
}
