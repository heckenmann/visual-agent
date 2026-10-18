package de.heckenmann.visualagent.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies independent capability evidence and alias normalization without provider heuristics. */
class CapabilitySupportTest {
    @Test
    fun `partial evidence never denies an unrelated capability`() {
        assertEquals(CapabilitySupport.UNKNOWN, capabilitySupport("chat", setOf("vision"), false))
        assertEquals(CapabilitySupport.UNKNOWN, capabilitySupport("tools", setOf("vision"), false, mapOf("vision" to false)))
        assertEquals(CapabilitySupport.UNSUPPORTED, capabilitySupport("chat", setOf("vision"), true))
    }

    @Test
    fun `targeted overrides take precedence and automatic restores provider evidence`() {
        val reported = setOf("completion", "function_calling")
        assertEquals(CapabilitySupport.SUPPORTED, capabilitySupport("chat", reported, true))
        assertEquals(CapabilitySupport.UNSUPPORTED, capabilitySupport("tools", reported, false, mapOf("TOOLS" to false)))
        assertEquals(CapabilitySupport.SUPPORTED, capabilitySupport("vision", reported, true, mapOf("image_input" to true)))
        assertEquals(CapabilitySupport.SUPPORTED, capabilitySupport("tools", reported, true, emptyMap()))
    }
}
