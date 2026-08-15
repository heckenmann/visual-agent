package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.ProtocolVersion
import de.heckenmann.visualagent.protocol.v1.ClientFrame
import io.grpc.stub.StreamObserver
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals("Cancelled by desktop", observer.values[1].cancelRequest.reason)
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
