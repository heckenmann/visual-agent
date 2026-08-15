package de.heckenmann.visualagent.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies that protocol compatibility has one centrally owned version. */
class ProtocolVersionTest {
    @Test
    fun `current protocol version is v1`() {
        assertEquals("v1", ProtocolVersion.CURRENT)
    }

    @Test
    fun `error categories are stable`() {
        assertEquals(
            setOf(
                ProtocolErrorCode.INCOMPATIBLE_PROTOCOL,
                ProtocolErrorCode.NOT_READY,
                ProtocolErrorCode.CANCELLED,
                ProtocolErrorCode.CONNECTION_LOST,
                ProtocolErrorCode.OPERATION_FAILED,
            ),
            ProtocolErrorCode.entries.toSet(),
        )
    }
}
