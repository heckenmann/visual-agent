package de.heckenmann.visualagent.agent.codex

import io.github.vupoint.cokit.protocol.JsonRpcNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Verifies that media-sized JSON-RPC notifications can cross the Codex stdio transport. */
class CodexCliCoKitTransportTest {
    @Test
    fun `accepts a notification larger than one megabyte`() =
        runTest {
            val process =
                ProcessBuilder(
                    "sh",
                    "-c",
                    "printf '{\"method\":\"large\",\"params\":\"'; " +
                        "head -c 1100000 /dev/zero | tr '\\0' 'a'; " +
                        "printf '\"}\\n'",
                ).start()
            val child = CodexCliChildProcess(process) { process.destroyForcibly() }
            val transport = CodexCliCoKitTransport(child, this)

            try {
                val message = withTimeout(10_000) { transport.incoming.first() }
                assertEquals("large", (message as JsonRpcNotification).method)
            } finally {
                transport.close()
            }
        }
}
