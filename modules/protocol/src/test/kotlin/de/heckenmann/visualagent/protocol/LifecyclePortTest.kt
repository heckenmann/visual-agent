package de.heckenmann.visualagent.protocol

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies the lifecycle boundary shared by desktop and server hosts. */
class LifecyclePortTest {
    @Test
    fun `shutdown transition is observable and idempotent`() {
        val lifecycle = LifecycleState()

        assertFalse(lifecycle.closing)
        lifecycle.beginShutdown()
        lifecycle.beginShutdown()

        assertTrue(lifecycle.closing)
    }
}
