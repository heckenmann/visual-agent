package de.heckenmann.visualagent.agent.codexdirect

import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class DirectTransportPoolTest {
    @Test
    fun `concurrent acquisitions build a single shared generation`() {
        val pool = DirectTransportPool()
        val executor = Executors.newFixedThreadPool(4)
        val creations = AtomicInteger()
        val resource = Resource()
        try {
            val leases =
                executor
                    .invokeAll(
                        List(20) {
                            Callable {
                                pool.acquire("same-policy") {
                                    creations.incrementAndGet()
                                    resource
                                }
                            }
                        },
                    ).map { it.get() }
            assertEquals(1, creations.get())
            leases.forEach { assertSame(resource, it.resource) }
            leases.forEach { it.close() }
            assertEquals(0, resource.closed)
        } finally {
            executor.shutdownNow()
            pool.destroy()
        }
        assertEquals(1, resource.closed)
    }

    @Test
    fun `same policy reuses transport and closing one lease does not close another`() {
        val pool = DirectTransportPool()
        val resource = Resource()
        val first = pool.acquire("v1") { resource }
        val second = pool.acquire("v1") { error("Transport should be reused") }
        assertSame(first.resource, second.resource)
        first.close()
        first.close()
        assertEquals(0, resource.closed)
        pool.destroy()
        assertEquals(0, resource.closed)
        second.close()
        assertEquals(1, resource.closed)
        pool.destroy()
        assertEquals(1, resource.closed)
        assertFailsWith<IllegalStateException> { pool.acquire("v1") { Resource() } }
    }

    @Test
    fun `trust replacement drains old requests and closes idle generations`() {
        val pool = DirectTransportPool()
        val old = Resource()
        val fresh = Resource()
        val first = pool.acquire("v1") { old }
        val next = pool.acquire("v2") { fresh }
        assertNotSame(first.resource, next.resource)
        assertEquals(0, old.closed)
        first.close()
        assertEquals(1, old.closed)
        next.close()
        assertEquals(0, fresh.closed)
        pool.destroy()
        assertEquals(1, fresh.closed)
    }

    @Test
    fun `unversioned policy never reuses transport and failed replacement preserves current`() {
        val pool = DirectTransportPool()
        val old = Resource()
        pool.acquire("v1") { old }.close()
        assertFailsWith<IllegalArgumentException> { pool.acquire("v2") { throw IllegalArgumentException("failed") } }
        pool.acquire("v1") { error("Existing transport must survive") }.close()
        val resource = Resource()
        pool.acquire(null) { resource }.close()
        assertEquals(1, old.closed)
        assertEquals(1, resource.closed)
        val different = Resource()
        pool.acquire(null) { different }.close()
        assertEquals(1, different.closed)
        pool.destroy()
    }

    private class Resource : AutoCloseable {
        var closed = 0

        override fun close() {
            closed++
        }
    }
}
