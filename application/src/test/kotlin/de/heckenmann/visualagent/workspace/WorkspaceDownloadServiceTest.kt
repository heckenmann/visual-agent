package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import de.heckenmann.visualagent.protocol.DownloadActivityStatus
import de.heckenmann.visualagent.protocol.WorkspaceDownloadState
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceDownloadServiceTest {
    @Test
    fun `download uses downloads default and detects content MIME`() {
        val dbPath = tempDir().resolve("data/visual-agent.db").toString()
        val store = FakeWorkspaceFileStore()
        val files = WorkspaceFileService(store, dbPath)
        val transfer = writingTransfer("<!doctype html><title>ok</title>".toByteArray())
        val service = WorkspaceDownloadService(files, transfer)

        val result = service.download(WorkspaceDownloadRequest("https://example.org/report.png"))

        assertEquals("downloads/report.png", result.relativePath)
        assertEquals("text/html", result.mimeType)
        assertEquals(1, store.records.size)
        assertTrue(files.resolveManagedPath(result.relativePath).exists())
    }

    @Test
    fun `download supports nested destination and unique names`() {
        val dbPath = tempDir().resolve("data/visual-agent.db").toString()
        val store = FakeWorkspaceFileStore()
        val files = WorkspaceFileService(store, dbPath)
        val service = WorkspaceDownloadService(files, writingTransfer(byteArrayOf(1, 2, 3)))

        val first = service.download(WorkspaceDownloadRequest("ftp://example.org/archive.bin", "downloads/reports", "summary.bin"))
        val second = service.download(WorkspaceDownloadRequest("ftp://example.org/archive.bin", "downloads/reports", "summary.bin"))

        assertEquals("downloads/reports/summary.bin", first.relativePath)
        assertEquals("downloads/reports/summary-1.bin", second.relativePath)
        assertTrue(files.resolveManagedPath(second.relativePath).readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `download publishes started and completed status events`() {
        val store = FakeWorkspaceFileStore()
        val files = WorkspaceFileService(store, tempDir().resolve("db.sqlite").toString())
        val events = mutableListOf<de.heckenmann.visualagent.protocol.DownloadActivity>()
        val eventBus = WorkspaceDownloadEventBus()
        eventBus.addListener(events::add)
        val service = WorkspaceDownloadService(files, writingTransfer(byteArrayOf(1, 2, 3)), eventBus = eventBus)

        service.download(WorkspaceDownloadRequest("https://example.org/file.bin"))

        assertEquals(
            listOf(DownloadActivityStatus.STARTED, DownloadActivityStatus.COMPLETED),
            events.map { it.status },
        )
        val completed = events.last()
        assertEquals("application/octet-stream", completed.mimeType)
        assertEquals(3L, completed.sizeBytes)
        assertEquals("accepted", completed.validationResult)
        assertTrue(completed.sha256.orEmpty().isNotBlank())
    }

    @Test
    fun `download rejects traversal credentials and unsupported protocols before transfer`() {
        var calls = 0
        val transfer = WorkspaceDownloadTransfer { _, _, _ -> calls++ }
        val service =
            WorkspaceDownloadService(
                WorkspaceFileService(FakeWorkspaceFileStore(), tempDir().resolve("db.sqlite").toString()),
                transfer,
            )

        assertFailsWith<RuntimeException> { service.download(WorkspaceDownloadRequest("https://example.org/file", "../outside")) }
        assertFailsWith<RuntimeException> { service.download(WorkspaceDownloadRequest("https://user:secret@example.org/file")) }
        assertFailsWith<RuntimeException> { service.download(WorkspaceDownloadRequest("https://example.org/file?api_key=secret")) }
        assertFailsWith<RuntimeException> { service.download(WorkspaceDownloadRequest("https://example.org/file?X-Amz-Signature=secret")) }
        assertFailsWith<RuntimeException> { service.download(WorkspaceDownloadRequest("gopher://example.org/file")) }

        assertEquals(0, calls)
    }

    @Test
    fun `download progress notifications are coalesced`() {
        val files = WorkspaceFileService(FakeWorkspaceFileStore(), tempDir().resolve("db.sqlite").toString())
        val service =
            WorkspaceDownloadService(
                files,
                WorkspaceDownloadTransfer { _, destination, control ->
                    repeat(200) { control.recordProgress(8_192) }
                    destination.writeBytes(byteArrayOf(1))
                },
            )
        var notifications = 0
        service.addListener { notifications++ }

        service.download(WorkspaceDownloadRequest("https://example.org/file.bin"))

        assertTrue(notifications < 10)
    }

    @Test
    fun `failed transfer removes temporary partial file`() {
        val root = tempDir()
        val store = FakeWorkspaceFileStore()
        val files = WorkspaceFileService(store, root.resolve("db/visual-agent.db").toString())
        val transfer =
            WorkspaceDownloadTransfer { _, destination, _ ->
                destination.writeBytes(byteArrayOf(1, 2))
                throw IOException("connection failed")
            }
        val service = WorkspaceDownloadService(files, transfer)

        assertFailsWith<RuntimeException> { service.download(WorkspaceDownloadRequest("sftp://user@example.org/file")) }

        val downloads = files.workspaceRoot().resolve("downloads")
        assertTrue(downloads.exists())
        assertFalse(Files.list(downloads).use { stream -> stream.anyMatch { it.fileName.toString().endsWith(".part") } })
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun `download can be paused and resumed`() {
        val root = tempDir()
        val store = FakeWorkspaceFileStore()
        val files = WorkspaceFileService(store, root.resolve("db/visual-agent.db").toString())
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val transfer =
            WorkspaceDownloadTransfer { _, destination, control ->
                started.countDown()
                while (release.count > 0) {
                    control.awaitReady()
                    destination.writeBytes(byteArrayOf(1))
                    control.recordProgress(1)
                    Thread.yield()
                }
            }
        val service = WorkspaceDownloadService(files, transfer)
        val worker = thread(start = true) { service.download(WorkspaceDownloadRequest("https://example.org/file.bin")) }

        started.await()
        while (service.activeDownloads().isEmpty()) Thread.yield()
        val id = service.activeDownloads().single().id
        service.pauseDownload(id)
        assertEquals(WorkspaceDownloadState.PAUSED, service.activeDownloads().single().state)
        service.resumeDownload(id)
        assertEquals(WorkspaceDownloadState.DOWNLOADING, service.activeDownloads().single().state)
        release.countDown()
        worker.join(5_000)
        assertTrue(store.records.isNotEmpty())
    }

    @Test
    fun `cancelling a download removes partial workspace data`() {
        val root = tempDir()
        val store = FakeWorkspaceFileStore()
        val files = WorkspaceFileService(store, root.resolve("db/visual-agent.db").toString())
        val started = CountDownLatch(1)
        val transfer =
            WorkspaceDownloadTransfer { _, destination, control ->
                started.countDown()
                while (!control.isCancelled()) {
                    control.awaitReady()
                    destination.writeBytes(byteArrayOf(1))
                    control.recordProgress(1)
                    Thread.yield()
                }
            }
        val service = WorkspaceDownloadService(files, transfer)
        val worker = thread(start = true) { runCatching { service.download(WorkspaceDownloadRequest("https://example.org/file.bin")) } }

        started.await()
        while (service.activeDownloads().isEmpty()) Thread.yield()
        service.cancelDownload(service.activeDownloads().single().id)
        worker.join(5_000)

        assertTrue(service.activeDownloads().isEmpty())
        assertTrue(store.records.isEmpty())
        val downloads = files.workspaceRoot().resolve("downloads")
        assertFalse(Files.list(downloads).use { stream -> stream.anyMatch { it.fileName.toString().endsWith(".part") } })
    }

    private fun writingTransfer(bytes: ByteArray) = WorkspaceDownloadTransfer { _, destination, _ -> destination.writeBytes(bytes) }

    private fun tempDir(): Path = Files.createTempDirectory("visual-agent-download-test")

    private class FakeWorkspaceFileStore : WorkspaceFileStore {
        val records = linkedMapOf<String, WorkspaceFileRecord>()

        override fun saveWorkspaceFile(record: WorkspaceFileRecord) {
            records[record.id] = record
        }

        override fun listWorkspaceFiles(): List<WorkspaceFileRecord> = records.values.toList()

        override fun getWorkspaceFile(id: String): WorkspaceFileRecord? = records[id]

        override fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord? =
            records.values.firstOrNull { it.relativePath == relativePath }

        override fun deleteWorkspaceFile(id: String): Boolean = records.remove(id) != null
    }
}
