package de.heckenmann.visualagent.workspace

import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Controls one transfer from the presentation-facing workspace download port. */
class WorkspaceDownloadControl(
    private val onProgress: (Long) -> Unit,
    private val onTotal: (Long) -> Unit,
) {
    private val lock = ReentrantLock()
    private val resumed = lock.newCondition()

    @Volatile
    private var paused = false

    @Volatile
    private var cancelled = false

    /** Number of bytes written to the temporary file. */
    @Volatile
    var downloadedBytes: Long = 0
        private set

    /** Known remote size, when the protocol provides it. */
    @Volatile
    var totalBytes: Long? = null
        private set

    /** Pauses after the current network read completes. */
    fun pause() {
        lock.withLock { paused = true }
    }

    /** Resumes a paused transfer. */
    fun resume() {
        lock.withLock {
            paused = false
            resumed.signalAll()
        }
    }

    /** Cancels the transfer and wakes a paused worker. */
    fun cancel() {
        lock.withLock {
            cancelled = true
            paused = false
            resumed.signalAll()
        }
    }

    /** Blocks while paused and throws when cancellation was requested. */
    fun awaitReady() {
        lock.withLock {
            try {
                while (paused && !cancelled) resumed.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw WorkspaceDownloadCancelledException()
            }
            if (cancelled) throw WorkspaceDownloadCancelledException()
        }
    }

    /** Returns whether cancellation was requested. */
    fun isCancelled(): Boolean = cancelled

    /** Records a known remote size for a determinate progress bar. */
    fun setTotalBytes(total: Long?) {
        if (total != null && total >= 0) {
            totalBytes = total
            onTotal(total)
        }
    }

    /** Records bytes copied and publishes progress. */
    fun recordProgress(bytes: Long) {
        downloadedBytes += bytes
        onProgress(downloadedBytes)
    }
}

internal class WorkspaceDownloadCancelledException : IOException("Download cancelled")

internal fun copyDownload(
    input: InputStream,
    output: OutputStream,
    control: WorkspaceDownloadControl? = null,
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        control?.awaitReady()
        if (Thread.currentThread().isInterrupted) throw WorkspaceDownloadCancelledException()
        val count = input.read(buffer)
        if (count < 0) return total
        total += count
        output.write(buffer, 0, count)
        control?.recordProgress(count.toLong())
    }
}

/** Output stream that applies download pause/cancellation and reports progress. */
internal class WorkspaceDownloadOutputStream(
    output: OutputStream,
    private val control: WorkspaceDownloadControl? = null,
) : FilterOutputStream(output) {
    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        control?.awaitReady()
        out.write(bytes, offset, length)
        control?.recordProgress(length.toLong())
    }

    override fun write(value: Int) {
        write(byteArrayOf(value.toByte()))
    }
}
