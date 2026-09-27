package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.ClientProcessInventoryRequest
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
    fun `chat submission carries the user text and separate identities`() {
        val observer = RecordingObserver<ClientFrame>()
        val session = DesktopServerSession(observer)

        val requestId = session.sendChat("hello")
        session.cancel(requestId)

        assertEquals(2, observer.values.size)
        assertEquals(requestId, observer.values[0].requestId)
        assertEquals(requestId, observer.values[1].requestId)
        assertTrue(UUID.fromString(requestId).toString() == requestId)
        assertTrue(UUID.fromString(observer.values[0].chatRequest.userEntryId).toString() == observer.values[0].chatRequest.userEntryId)
        assertTrue(requestId != observer.values[0].chatRequest.userEntryId)
        assertEquals(requestId, observer.values[0].chatRequest.assistantEntryId)
        assertEquals("hello", observer.values[0].chatRequest.content)
        assertEquals("Cancelled by desktop", observer.values[1].cancelRequest.reason)
    }

    @Test
    fun `chat text is sent in the user submission without waiting for a server request`() {
        val observer = RecordingObserver<ClientFrame>()
        val session = DesktopServerSession(observer)

        session.sendChat("hello")
        assertEquals(1, observer.values.size)
        assertEquals(ClientFrame.PayloadCase.CHAT_REQUEST, observer.values.single().payloadCase)
        assertEquals(
            "hello",
            observer.values
                .single()
                .chatRequest.content,
        )
    }

    @Test
    fun `desktop process inventory is bounded by explicit pagination at the model tool`() {
        val snapshot = JvmClientProcessInventoryDiagnosticsPort().snapshot(ClientProcessInventoryRequest("list", 0, 1, null))

        assertEquals(1, snapshot.processes.size)
        assertTrue(snapshot.totalProcesses >= snapshot.processes.size)
        assertTrue(snapshot.processes.all { it.pid >= 0 })
        assertEquals(snapshot.processes.size, snapshot.processes.distinctBy { it.pid }.size)
        assertEquals(0, snapshot.offset)
        assertEquals(snapshot.totalProcesses > snapshot.processes.size, snapshot.hasMore)
    }

    @Test
    fun `desktop process inventory show returns only the requested pid`() {
        val pid = ProcessHandle.current().pid()
        val snapshot = JvmClientProcessInventoryDiagnosticsPort().snapshot(ClientProcessInventoryRequest("show", 0, 1, pid))

        assertEquals(0, snapshot.offset)
        assertEquals(1, snapshot.processes.size)
        assertEquals(pid, snapshot.processes.single().pid)
        assertEquals(false, snapshot.hasMore)
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

    private class RecordingObserver<T> : StreamObserver<T> {
        val values = mutableListOf<T>()

        override fun onNext(value: T) {
            values += value
        }

        override fun onError(throwable: Throwable) = Unit

        override fun onCompleted() = Unit
    }
}
