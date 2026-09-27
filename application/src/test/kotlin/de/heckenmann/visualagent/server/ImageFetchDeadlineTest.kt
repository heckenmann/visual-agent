package de.heckenmann.visualagent.server

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageFetchDeadlineTest {
    @Test
    fun `returns a completed image fetch`() {
        val executor = mockk<ExecutorService>()
        val task = mockk<Future<Int>>()
        every { executor.submit(any<Callable<Int>>()) } returns task
        every { task.get(15_000L, TimeUnit.MILLISECONDS) } returns 42

        assertEquals(42, fetchBeforeDeadline(executor, 15_000) { 42 })
        verify(exactly = 0) { task.cancel(any()) }
    }

    @Test
    fun `cancels an image fetch when the total deadline expires`() {
        val executor = mockk<ExecutorService>()
        val task = mockk<Future<Int>>()
        every { executor.submit(any<Callable<Int>>()) } returns task
        every { task.get(15_000L, TimeUnit.MILLISECONDS) } throws TimeoutException()
        every { task.cancel(true) } returns true

        assertFailsWith<TimeoutException> { fetchBeforeDeadline(executor, 15_000) { 42 } }
        verify(exactly = 1) { task.cancel(true) }
    }
}
