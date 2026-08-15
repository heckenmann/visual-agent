package de.heckenmann.visualagent.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies the lifecycle state exposed to desktop clients. */
class ApplicationLifecycleTest {
    @Test
    fun `shutdown marks lifecycle as closing`() {
        val lifecycle = ApplicationLifecycle()

        assertFalse(lifecycle.closing)
        lifecycle.beginShutdown()
        assertTrue(lifecycle.closing)
    }
}
