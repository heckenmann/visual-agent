package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.ClientRuntimeDiagnosticsPort
import de.heckenmann.visualagent.protocol.ClientRuntimeSnapshot
import de.heckenmann.visualagent.protocol.ProtocolVersion
import de.heckenmann.visualagent.protocol.v1.ClientFrame
import io.grpc.stub.StreamObserver
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Verifies that the desktop emits only protocol frames for session operations. */
class DesktopServerSessionTest {
    @Test
    fun `hello frame carries the current protocol version`() {
        val observer = RecordingObserver<ClientFrame>()
        val session = DesktopServerSession(observer)

        session.sendHello()

        val frame = observer.values.single()
        assertEquals(ProtocolVersion.CURRENT, frame.hello.protocolVersion)
        assertEquals("visual-agent-desktop", frame.hello.clientName)
        assertTrue(frame.sessionId.isNotBlank())
    }

    @Test
    fun `chat and cancel frames carry a stable request id`() {
        val observer = RecordingObserver<ClientFrame>()
        val session = DesktopServerSession(observer)

        val requestId = session.sendChat("hello")
        session.cancel(requestId)

        assertEquals(2, observer.values.size)
        assertEquals(requestId, observer.values[0].requestId)
        assertEquals(requestId, observer.values[1].requestId)
        assertEquals("hello", observer.values[0].chatRequest.content)
        assertTrue(UUID.fromString(requestId).toString() == requestId)
        assertTrue(UUID.fromString(observer.values[0].chatRequest.userEntryId).toString() == observer.values[0].chatRequest.userEntryId)
        assertTrue(requestId != observer.values[0].chatRequest.userEntryId)
        assertEquals("Cancelled by desktop", observer.values[1].cancelRequest.reason)
    }

    @Test
    fun `chat frame carries a separately supplied client runtime snapshot`() {
        val observer = RecordingObserver<ClientFrame>()
        val snapshot = sampleSnapshot()
        val session = DesktopServerSession(observer, ClientRuntimeDiagnosticsPort { snapshot })

        session.sendChat("hello")

        val chatRequest = observer.values.single().chatRequest
        assertTrue(chatRequest.hasClientRuntime())
        val runtime = chatRequest.clientRuntime
        assertEquals(snapshot.processId, runtime.processId)
        assertEquals(snapshot.osName, runtime.osName)
        assertEquals(snapshot.heapMaxBytes, runtime.heapMaxBytes)
        assertEquals(snapshot.processCpuLoad, runtime.processCpuLoad)
    }

    @Test
    fun `desktop runtime snapshot contains bounded values from the client JVM`() {
        val snapshot = JvmClientRuntimeDiagnosticsPort().snapshot()

        assertNotNull(snapshot)
        assertTrue(snapshot.processId > 0)
        assertTrue(snapshot.availableProcessors > 0)
        assertTrue(snapshot.heapUsedBytes >= 0)
        assertTrue(snapshot.heapCommittedBytes >= snapshot.heapUsedBytes)
        assertTrue(snapshot.osName.length <= 96)
        assertTrue(snapshot.javaVersion.length <= 96)
        snapshot.processCpuLoad?.let { assertTrue(it in 0.0..1.0) }
    }

    private fun sampleSnapshot() =
        ClientRuntimeSnapshot(
            processId = 42,
            osName = "Test OS",
            osVersion = "1",
            architecture = "test-arch",
            availableProcessors = 2,
            javaVersion = "24",
            jvmVendor = "Test Vendor",
            vmName = "Test VM",
            uptimeMillis = 10,
            heapUsedBytes = 100,
            heapCommittedBytes = 200,
            heapMaxBytes = 300,
            totalPhysicalMemoryBytes = 400,
            freePhysicalMemoryBytes = 200,
            processCpuLoad = 0.25,
        )

    private class RecordingObserver<T> : StreamObserver<T> {
        val values = mutableListOf<T>()

        override fun onNext(value: T) {
            values += value
        }

        override fun onError(throwable: Throwable) = Unit

        override fun onCompleted() = Unit
    }
}
