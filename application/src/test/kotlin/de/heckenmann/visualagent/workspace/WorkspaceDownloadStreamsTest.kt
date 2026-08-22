package de.heckenmann.visualagent.workspace

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies unbounded stream copying and cooperative download control. */
class WorkspaceDownloadStreamsTest {
    @Test
    fun `copy reports progress and total`() {
        val control = WorkspaceDownloadControl({}, {})
        control.setTotalBytes(3)
        val output = ByteArrayOutputStream()

        val copied = copyDownload(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, control)

        assertEquals(3, copied)
        assertEquals(3, control.downloadedBytes)
        assertEquals(3, control.totalBytes)
        assertContentEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }

    @Test
    fun `copy does not impose an artificial size limit`() {
        val size = 50L * 1024L * 1024L + 1L
        val copied = copyDownload(FixedSizeInputStream(size), OutputStream.nullOutputStream())

        assertEquals(size, copied)
    }

    @Test
    fun `copy rejects cancelled streams`() {
        val control = WorkspaceDownloadControl({}, {})
        control.cancel()
        assertFailsWith<WorkspaceDownloadCancelledException> {
            copyDownload(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), control)
        }
    }

    @Test
    fun `download output stream records progress without a size limit`() {
        val control = WorkspaceDownloadControl({}, {})
        val output = ByteArrayOutputStream()
        val stream = WorkspaceDownloadOutputStream(output, control)

        stream.write(byteArrayOf(1, 2, 3))

        assertEquals(3, control.downloadedBytes)
    }

    private class FixedSizeInputStream(
        private val size: Long,
    ) : InputStream() {
        private var remaining = size

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (remaining == 0L) return -1
            val count = minOf(remaining, length.toLong()).toInt()
            java.util.Arrays.fill(buffer, offset, offset + count, 0.toByte())
            remaining -= count
            return count
        }

        override fun read(): Int = if (read(byteArrayOf(0), 0, 1) < 0) -1 else 0
    }
}
