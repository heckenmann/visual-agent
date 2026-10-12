package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.workspace.WorkspaceFileService
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Trusted inspection receipt, never supplied by worker JSON or inferred from model approval. */
data class TaskReviewEvidence(
    val text: String,
    val artifactHashes: Map<String, String>,
)

/** Reads only supported complete UTF-8 content and records precisely which versions fit the review budget. */
internal fun readTaskReviewEvidence(
    workspace: WorkspaceFileService,
    contract: TaskContract,
    requireResource: (TaskContract, String) -> Unit,
): TaskReviewEvidence {
    val content = StringBuilder()
    val inspected = mutableMapOf<String, String>()
    val references =
        contract.deliverables.map { Triple(it.id, it.path, true) } +
            contract.inputs.mapNotNull { input -> input.path?.let { Triple(input.id, it, false) } }
    references.take(32).forEach { (id, path, artifact) ->
        val header = "${if (artifact) "Artifact" else "Input"} $id ($path):\n"
        if (content.length + header.length + 100 < 24_000) {
            content.append(header)
            runCatching {
                requireResource(contract, path)
                val record = workspace.requireFile(null, path)
                require(textReviewMime(record.mimeType)) { "Unsupported binary evidence" }
                val bytes = workspace.readBytes(record, 1_048_576)
                val text =
                    Charsets.UTF_8
                        .newDecoder()
                        .decode(ByteBuffer.wrap(bytes))
                        .toString()
                val capacity = minOf(8_000, 24_000 - content.length - 100)
                content.appendLine(text.take(capacity))
                if (text.length > capacity) {
                    content.appendLine("NOT_CHECKED: Remaining content omitted by review budget")
                } else if (artifact) {
                    inspected[id] = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                }
            }.onFailure { content.appendLine("NOT_CHECKED: Content unavailable, non-UTF-8, or requires an unsupported binary inspector") }
        }
    }
    return TaskReviewEvidence(content.toString(), inspected.toMap())
}

private fun textReviewMime(mime: String): Boolean {
    val type = mime.substringBefore(';').trim().lowercase()
    return type.startsWith("text/") ||
        type.endsWith("+json") ||
        type.endsWith("+xml") ||
        type in setOf("application/json", "application/xml", "application/javascript", "application/x-javascript", "application/yaml")
}
