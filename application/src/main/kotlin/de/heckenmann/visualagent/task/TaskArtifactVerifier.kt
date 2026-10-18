package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.workspace.WorkspaceFileService
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/** Checks current bounded workspace bytes, never persisted metadata or worker success claims. */
@Service
class TaskArtifactVerifier(
    private val workspace: WorkspaceFileService,
) {
    /** Snapshot each declared deliverable and evaluate deterministic acceptance criteria. */
    fun observe(contract: TaskContract): Pair<List<ObservedArtifact>, List<TaskObservation>> {
        val bytes = mutableMapOf<String, ByteArray>()
        val artifacts =
            contract.deliverables.map { artifact ->
                runCatching {
                    requireResource(contract, artifact.path)
                    val root = workspace.workspaceRoot().toRealPath()
                    val file = root.resolve(artifact.path).normalize()
                    require(file.startsWith(root) && file.toRealPath().startsWith(root)) { "Artifact escapes the workspace" }
                    require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { "Artifact is not a regular file" }
                    val record = workspace.requireFile(null, artifact.path)
                    val content = workspace.readBytes(record, 1_048_576)
                    bytes[artifact.id] = content
                    ObservedArtifact(
                        artifact.id,
                        artifact.path,
                        MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) },
                        record.mimeType,
                        content.size.toLong(),
                        TaskCheckState.PASSED,
                        "Current artifact bytes captured",
                    )
                }.getOrElse {
                    ObservedArtifact(
                        artifact.id,
                        artifact.path,
                        state = TaskCheckState.FAILED,
                        detail = "Artifact missing, inaccessible, or exceeds the 1 MiB verification limit",
                    )
                }
            }
        val observations =
            contract.criteria.map { criterion ->
                val content = bytes[criterion.artifactId]
                when {
                    criterion.method == TaskCheckMethod.MANUAL ->
                        TaskObservation(
                            criterion.id,
                            TaskCheckState.NOT_CHECKED,
                            "Requires independent review of actual evidence",
                        )
                    content == null -> TaskObservation(criterion.id, TaskCheckState.FAILED, "Required artifact could not be observed")
                    else -> {
                        val text = content.toString(Charsets.UTF_8)
                        val passed =
                            when (criterion.method) {
                                TaskCheckMethod.FILE_EXISTS -> true
                                TaskCheckMethod.TEXT_CONTAINS -> text.contains(criterion.expected!!)
                                TaskCheckMethod.JSON_VALID ->
                                    runCatching {
                                        val decoded = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(content))
                                        Json.parseToJsonElement(decoded.toString())
                                    }.isSuccess
                                TaskCheckMethod.SOURCE_QUOTE -> sourceContains(contract, criterion) && text.contains(criterion.expected!!)
                                TaskCheckMethod.MANUAL -> false
                            }
                        TaskObservation(
                            criterion.id,
                            if (passed) TaskCheckState.PASSED else TaskCheckState.FAILED,
                            "${criterion.method}: ${if (passed) "observed" else "evidence does not match"}",
                        )
                    }
                }
            }
        return artifacts to observations
    }

    private fun sourceContains(
        contract: TaskContract,
        criterion: TaskCriterion,
    ): Boolean =
        runCatching {
            val input = contract.inputs.single { it.id == criterion.sourceInputId }
            requireResource(contract, input.path!!)
            val root = workspace.workspaceRoot().toRealPath()
            val path = root.resolve(input.path!!).toRealPath()
            require(path.startsWith(root))
            workspace.readBytes(workspace.requireFile(null, input.path), 1_048_576).toString(Charsets.UTF_8).contains(criterion.expected!!)
        }.getOrDefault(false)

    /** Supplies bounded current artifact and selected input content to an independent reviewer. */
    fun reviewEvidence(contract: TaskContract): String = reviewSnapshot(contract).text

    /** Server-owned record of complete supported artifact versions actually supplied to review. */
    fun reviewSnapshot(contract: TaskContract): TaskReviewEvidence = readTaskReviewEvidence(workspace, contract, ::requireResource)

    private fun requireResource(
        contract: TaskContract,
        path: String,
    ) {
        require(
            contract.policy.resources?.any { path == it || path.startsWith("$it/") } ?: true,
        ) { "Evidence is outside the task resource scope" }
        require(resourceAllowed(contract, path)) { "Evidence path crosses a symbolic link" }
    }

    /** Resolves each path component so a permitted folder cannot alias another resource. */
    fun resourceAllowed(
        contract: TaskContract,
        path: String,
    ): Boolean =
        runCatching {
            TaskContractCodec.validatePath(path)
            require(contract.policy.resources?.any { path == it || path.startsWith("$it/") } ?: true)
            var current = workspace.workspaceRoot().toRealPath()
            path.split('/').forEach { component ->
                current = current.resolve(component)
                require(!Files.isSymbolicLink(current))
            }
            true
        }.getOrDefault(false)

    /** Pins actual selected input bytes before a worker can change workspace content. */
    fun inputVersions(contract: TaskContract): Map<String, String?> =
        contract.inputs.filter { it.path != null }.associate { input ->
            input.id to
                runCatching {
                    requireResource(contract, input.path!!)
                    val bytes = workspace.readBytes(workspace.requireFile(null, input.path), 1_048_576)
                    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                }.getOrNull()
        }

    /** Detects content changes between verification and acceptance. */
    fun unchanged(
        contract: TaskContract,
        expected: List<ObservedArtifact>,
    ): Boolean {
        val (artifacts, checks) = observe(contract)
        return artifacts == expected &&
            contract.criteria.filter { it.required && it.method != TaskCheckMethod.MANUAL }.all { criterion ->
                checks.any { it.criterionId == criterion.id && it.state == TaskCheckState.PASSED }
            }
    }
}
