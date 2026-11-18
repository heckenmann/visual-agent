package de.heckenmann.visualagent.release

import de.heckenmann.visualagent.release.bundles.BundleRequest
import java.nio.file.Path

/** Shares the existing named-path option handling across notice and bundle commands. */
internal fun namedPathOptions(
    arguments: List<String>,
    allowed: Set<String>,
): Map<String, Path> {
    val paths = mutableMapOf<String, Path>()
    var index = 0
    while (index < arguments.size) {
        val token = arguments[index++]
        require(token.startsWith("--")) { "Expected a named path option: $token" }
        val key = token.removePrefix("--").substringBefore('=')
        require(key in allowed && key !in paths) { "Unknown or duplicate path option: $key" }
        val value =
            if ('=' in token) {
                token.substringAfter('=')
            } else {
                require(index < arguments.size && !arguments[index].startsWith("--")) { "Missing path option value: $key" }
                arguments[index++]
            }
        require(value.isNotEmpty()) { "Empty path option value: $key" }
        paths[key] = Path.of(value)
    }
    return paths
}

/** Parses review/recipient archive inputs with an optional repository-root override. */
internal fun bundleRequest(arguments: List<String>): BundleRequest {
    val recipient = "--recipient-candidate" in arguments
    val paths =
        namedPathOptions(
            arguments.filter { it != "--recipient-candidate" },
            setOf("project-root", "source-materials", "notice-evidence", "output-directory"),
        )

    /** Rejects omitted bundle inputs before producing archives. */
    fun required(key: String): Path = requireNotNull(paths[key]) { "Missing required bundle option: --$key" }
    return BundleRequest(
        paths["project-root"] ?: Path.of("."),
        required("source-materials"),
        required("notice-evidence"),
        required("output-directory"),
        recipient,
    )
}
