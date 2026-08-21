package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.error.WorkspaceFileException
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.protocol.DownloadActivity
import de.heckenmann.visualagent.protocol.DownloadActivityStatus
import de.heckenmann.visualagent.protocol.WorkspaceDownload
import de.heckenmann.visualagent.protocol.WorkspaceDownloadState
import org.apache.tika.Tika
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.URI
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize
import kotlin.io.path.name

/** Coordinates secure remote downloads and registration in the managed workspace. */
@Service
class WorkspaceDownloadService(
    private val workspaceFiles: WorkspaceFileService,
    private val transfer: WorkspaceDownloadTransfer,
    private val mimeDetector: Tika = Tika(),
    private val eventBus: WorkspaceDownloadEventBus = WorkspaceDownloadEventBus(),
) {
    private val jobs = ConcurrentHashMap<String, WorkspaceDownloadJob>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** Downloads, validates, atomically publishes, and registers one remote file. */
    fun download(request: WorkspaceDownloadRequest): WorkspaceFileRecord {
        var temporary: Path? = null
        val target = normalizeTarget(request)
        val id = UUID.randomUUID().toString()
        lateinit var job: WorkspaceDownloadJob
        val control =
            WorkspaceDownloadControl(
                onProgress = { notifyListeners() },
                onTotal = { notifyListeners() },
            )
        job = WorkspaceDownloadJob(id, relativePath(target), control)
        jobs[id] = job
        publishStatus(job, DownloadActivityStatus.STARTED)
        notifyListeners()
        try {
            val temp = Files.createTempFile(target.directory, ".visual-agent-download-", ".part")
            temporary = temp
            transfer.download(target.source, temp, control)
            val size = temp.fileSize()
            require(size > 0) { "Downloaded file is empty" }
            control.awaitReady()
            val detectedMimeType = detectMimeType(temp)
            val destination = publish(temp, target)
            if (control.isCancelled()) {
                destination.deleteIfExists()
                throw WorkspaceDownloadCancelledException()
            }
            return runCatching {
                workspaceFiles.registerDownloadedFile(destination, destination.name, detectedMimeType)
            }.getOrElse { error ->
                destination.deleteIfExists()
                throw error
            }.also { publishStatus(job, DownloadActivityStatus.COMPLETED) }
        } catch (error: WorkspaceFileException) {
            if (!control.isCancelled()) publishStatus(job, DownloadActivityStatus.FAILED, error.message)
            throw error
        } catch (_: WorkspaceDownloadCancelledException) {
            throw WorkspaceFileException("Download cancelled", "The remote download was cancelled before completion.", true)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw WorkspaceFileException("Download cancelled", "The remote download was cancelled before completion.", true)
        } catch (error: Exception) {
            if (!control.isCancelled()) publishStatus(job, DownloadActivityStatus.FAILED, safeFailure(error))
            throw WorkspaceFileException("Download failed", safeFailure(error), true)
        } finally {
            jobs.remove(id)
            notifyListeners()
            temporary?.deleteIfExists()
        }
    }

    /** Returns downloads that are currently running or paused. */
    fun activeDownloads(): List<WorkspaceDownload> =
        jobs.values
            .map { job ->
                WorkspaceDownload(
                    id = job.id,
                    relativePath = job.relativePath,
                    state = if (job.paused) WorkspaceDownloadState.PAUSED else WorkspaceDownloadState.DOWNLOADING,
                    downloadedBytes = job.control.downloadedBytes,
                    totalBytes = job.control.totalBytes,
                )
            }.sortedBy(WorkspaceDownload::relativePath)

    /** Pauses one active download. */
    fun pauseDownload(id: String) {
        jobs[id]?.let { job ->
            if (job.paused) return@let
            job.paused = true
            job.control.pause()
            publishStatus(job, DownloadActivityStatus.PAUSED)
            notifyListeners()
        }
    }

    /** Resumes one paused download. */
    fun resumeDownload(id: String) {
        jobs[id]?.let { job ->
            if (!job.paused) return@let
            job.paused = false
            job.control.resume()
            publishStatus(job, DownloadActivityStatus.RESUMED)
            notifyListeners()
        }
    }

    /** Cancels one active download and removes its partial file. */
    fun cancelDownload(id: String) {
        jobs.remove(id)?.let { job ->
            job.control.cancel()
            publishStatus(job, DownloadActivityStatus.CANCELLED)
            notifyListeners()
        }
    }

    /** Registers a listener for download progress changes. */
    fun addListener(listener: () -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    private fun normalizeTarget(request: WorkspaceDownloadRequest): WorkspaceDownloadTarget {
        val source = parseSource(request.source)
        val remotePath = source.path.trim()
        require(remotePath.isNotBlank()) { "Remote source path is missing" }
        val root = workspaceFiles.workspaceRoot().toAbsolutePath().normalize()
        val directory = resolveDirectory(root, request.directory)
        val requestedName = request.filename?.let(::validateFileName) ?: deriveFileName(remotePath)
        return WorkspaceDownloadTarget(source, directory, requestedName)
    }

    private fun relativePath(target: WorkspaceDownloadTarget): String =
        workspaceFiles
            .workspaceRoot()
            .relativize(target.directory.resolve(target.requestedName))
            .toString()
            .replace('\\', '/')

    private fun notifyListeners() {
        listeners.forEach { listener -> runCatching(listener) }
    }

    private fun publishStatus(
        job: WorkspaceDownloadJob,
        status: DownloadActivityStatus,
        detail: String? = null,
    ) {
        eventBus.publish(
            DownloadActivity(
                id = job.id,
                relativePath = job.relativePath,
                status = status,
                downloadedBytes = job.control.downloadedBytes,
                totalBytes = job.control.totalBytes,
                detail = detail,
            ),
        )
    }

    private fun parseSource(raw: String): URI {
        val source = runCatching { URI(raw.trim()) }.getOrNull() ?: throw IllegalArgumentException("Remote source is invalid")
        require(source.scheme?.lowercase() in SUPPORTED_SCHEMES) { "Download protocol is not supported" }
        require(source.fragment == null) { "Remote source fragments are not supported" }
        require(source.host.isNullOrBlank().not()) { "Remote source host is missing" }
        if (source.scheme.equals("http", true) || source.scheme.equals("https", true) || source.scheme.equals("ftp", true)) {
            require(source.userInfo == null) { "Credentials are not accepted in a remote source" }
        } else {
            require(source.userInfo?.contains(':') != true) { "Passwords are not accepted in a remote source" }
        }
        return source
    }

    private fun resolveDirectory(
        root: Path,
        requested: String?,
    ): Path {
        val value = requested?.trim().orEmpty().ifBlank { DEFAULT_DIRECTORY }
        val normalized = value.replace('\\', '/')
        val path = Path.of(normalized)
        require(!path.isAbsolute && normalized.isNotBlank()) { "Download directory must be workspace-relative" }
        val segments = normalized.split('/').filter { it.isNotBlank() }
        require(segments.none { it == ".." || it == "." }) { "Download directory traversal is not allowed" }
        val directory = root.resolve(normalized).normalize()
        require(directory.startsWith(root)) { "Download directory escapes the workspace" }
        directory.createDirectories()
        require(directory.toRealPath().startsWith(root.toRealPath())) { "Download directory escapes the workspace" }
        return directory
    }

    private fun validateFileName(raw: String): String {
        val value = raw.trim()
        require(value.isNotBlank() && value != "." && value != "..") { "Download filename is invalid" }
        require('/' !in value && '\\' !in value && '\u0000' !in value) { "Download filename must not contain a path" }
        return WorkspaceFilePaths.safeFileName(value)
    }

    private fun deriveFileName(remotePath: String): String {
        val name = remotePath.substringAfterLast('/').ifBlank { DEFAULT_FILE_NAME }
        return validateFileName(name)
    }

    private fun detectMimeType(path: Path): String {
        val bytes = Files.newInputStream(path).use { it.readNBytes(MAX_MIME_DETECTION_BYTES) }
        require(bytes.isNotEmpty()) { "Downloaded file is empty" }
        return mimeDetector.detect(bytes).trim().ifBlank { "application/octet-stream" }
    }

    private fun publish(
        temporary: Path,
        target: WorkspaceDownloadTarget,
    ): Path =
        synchronized(destinationLock) {
            var destination = WorkspaceFilePaths.uniqueDestination(target.directory, target.requestedName)
            try {
                moveAtomically(temporary, destination)
            } catch (_: FileAlreadyExistsException) {
                destination = WorkspaceFilePaths.uniqueDestination(target.directory, target.requestedName)
                moveAtomically(temporary, destination)
            }
            destination
        }

    private fun moveAtomically(
        source: Path,
        destination: Path,
    ) {
        runCatching {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
        }.recoverCatching {
            Files.move(source, destination)
        }.getOrThrow()
    }

    private fun safeFailure(error: Exception): String =
        when (error) {
            is IllegalArgumentException -> error.message ?: "The remote source or destination is invalid"
            is IOException -> error.message ?: "The remote transfer could not be completed"
            else -> "The remote transfer could not be completed"
        }.replace(Regex("(?i)(password|token|secret|api[_-]?key)\\s*[=:]\\s*[^, ]+"), "credential=[redacted]")

    private companion object {
        const val DEFAULT_DIRECTORY = "downloads"
        const val DEFAULT_FILE_NAME = "download"
        const val MAX_MIME_DETECTION_BYTES = 1024 * 1024
        val SUPPORTED_SCHEMES = setOf("http", "https", "ftp", "sftp", "scp")
        val destinationLock = Any()
    }
}
