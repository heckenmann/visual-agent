package de.heckenmann.visualagent.release

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
