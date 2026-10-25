package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDownloadPhase
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceDownloadPublicationTest {
    @Test
    fun `pause cannot publish ahead of progress that already owns publication`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val seen = CopyOnWriteArrayList<String>()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val reporter =
            WorkspaceDownloadLiveReporter {
                if (it.status == "Downloading") {
                    entered.countDown()
                    release.await()
                }
                seen.add(it.status)
            }
        reporter.phase(ToolDownloadPhase.STARTED)
        val pauseDone = AtomicBoolean(false)
        val progress =
            Thread {
                try {
                    runCatching { reporter.progress(1, 10) }.onFailure(failures::add)
                } finally {
                    entered.countDown()
                }
            }
        val pause =
            Thread {
                try {
                    runCatching { reporter.phase(ToolDownloadPhase.PAUSED) }.onFailure(failures::add)
                } finally {
                    pauseDone.set(true)
                }
            }
        progress.start()
        try {
            entered.await()
            pause.start()
            // Observe actual monitor contention or completed publication, never an elapsed-time threshold.
            val threads = ManagementFactory.getThreadMXBean()
            while (!pauseDone.get()) {
                if (threads.getThreadInfo(pause.threadId())?.lockOwnerId == progress.threadId()) break
                Thread.onSpinWait()
            }
        } finally {
            release.countDown()
            progress.join()
            pause.join()
        }
        assertTrue(failures.isEmpty())
        assertEquals(listOf("Connecting", "Downloading", "Paused"), seen.toList())
    }
}
