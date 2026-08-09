package de.heckenmann.visualagent.ui.agents

internal fun Map<String, String>.toOptionsText(): String =
    entries
        .sortedBy { it.key }
        .joinToString("\n") { "${it.key}=${it.value}" }

internal fun String.toOptionsMapOrNull(): Map<String, String>? {
    val result = linkedMapOf<String, String>()
    lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .forEach { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return null
            val key = line.take(separatorIndex).trim()
            val value = line.drop(separatorIndex + 1).trim()
            if (key.isBlank()) return null
            result[key] = value
        }
    return result
}

internal fun String.optionalDoubleIsValid(): Boolean = isBlank() || toDoubleOrNull() != null

internal fun String.optionalIntIsValid(): Boolean = isBlank() || toIntOrNull() != null

internal const val KEEP_AGENT_CONFIG = "__keep__"
internal const val INHERIT_SELECTION = "__inherit__"
