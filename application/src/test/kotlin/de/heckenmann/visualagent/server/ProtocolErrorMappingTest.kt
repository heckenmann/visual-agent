package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ProtocolErrorCategory
import de.heckenmann.visualagent.protocol.ProtocolOperationException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Verifies safe protocol error conversion and cancellation propagation. */
class ProtocolErrorMappingTest {
    @Test
    fun `generic failure becomes a safe structured protocol error`() {
        val failure = IllegalStateException("provider response contains a secret")

        val mapped = assertFailsWith<ProtocolOperationException> { protocolBoundary { throw failure } }

        assertEquals(ProtocolErrorCategory.UNKNOWN, mapped.error.category)
        assertEquals("Request failed", mapped.error.summary)
        assertSame(failure, mapped.cause)
    }

    @Test
    fun `existing protocol error is not wrapped twice`() {
        val original =
            ProtocolOperationException(
                error =
                    de.heckenmann.visualagent.protocol.UserFacingError(
                        ProtocolErrorCategory.WORKSPACE,
                        "Import failed",
                        "Choose a smaller file.",
                    ),
            )

        val mapped = assertFailsWith<ProtocolOperationException> { protocolBoundary { throw original } }

        assertSame(original, mapped)
    }

    @Test
    fun `cancellation is propagated without conversion`() {
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> { protocolBoundary { throw cancellation } }

        assertSame(cancellation, thrown)
    }
}
