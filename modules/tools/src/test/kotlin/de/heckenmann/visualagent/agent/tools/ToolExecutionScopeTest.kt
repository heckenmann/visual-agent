package de.heckenmann.visualagent.agent.tools

import reactor.core.Disposables
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies attempt ownership independently of provider timing. */
class ToolExecutionScopeTest {
    @Test
    fun `one completed background call does not complete the attempt`() {
        val scope = ToolExecutionScope()
        val first = scope.register("first", true)
        val second = scope.register("second", true)
        val completed = AtomicBoolean()
        scope.awaitCompletion().subscribe({}, { throw it }, { completed.set(true) })
        first.finish(success("first", "first result"))
        assertFalse(completed.get())
        second.finish(success("second", "second result"))
        assertTrue(completed.get())
        assertEquals(2, scope.asynchronousCount())
        assertContains(scope.evidence(), "second result")
    }

    @Test
    fun `work registered by a running parent is also awaited`() {
        val scope = ToolExecutionScope()
        val parent = scope.register("parent", true)
        val completed = AtomicBoolean()
        scope.awaitCompletion().subscribe({}, { throw it }, { completed.set(true) })
        val child = scope.register("child", true)
        parent.finish(success("parent", "Parent result"))
        assertFalse(completed.get())
        child.finish(success("child", "Child result"))
        assertTrue(completed.get())
    }

    @Test
    fun `closing an attempt disposes work and prevents late success`() {
        val scope = ToolExecutionScope()
        val call = scope.register("background", true)
        val subscription = Disposables.single()
        call.attach(subscription)
        scope.close()
        assertTrue(subscription.isDisposed)
        call.finish(success("background", "late success"))
        assertContains(scope.evidence(), "TOOL_CANCELLED")
        assertFalse(scope.evidence().contains("late success"))
        scope.awaitCompletion().block()
    }

    @Test
    fun `subscription attached after cancellation is immediately disposed`() {
        val scope = ToolExecutionScope()
        val call = scope.register("background", true)
        scope.close()
        val subscription = Disposables.single()
        call.attach(subscription)
        assertTrue(subscription.isDisposed)
    }

    @Test
    fun `evidence includes failure and remains isolated between attempts`() {
        val first = ToolExecutionScope()
        first.register("write", false).finish(failure("write", "permission denied"))
        val second = ToolExecutionScope()
        assertContains(first.evidence(), "FAILURE")
        assertContains(first.evidence(), "permission denied")
        assertFalse(second.evidence().contains("permission denied"))
    }
}
