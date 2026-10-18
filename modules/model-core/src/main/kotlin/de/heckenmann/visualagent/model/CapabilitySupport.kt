package de.heckenmann.visualagent.model

/** Evidence for one model feature, independent of all other features. */
enum class CapabilitySupport {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

/** Normalizes capability aliases consistently across server and presentation boundaries. */
fun capabilityKey(value: String): String =
    when (val key = value.trim().lowercase().replace('-', '_')) {
        "completion", "generate_text" -> "chat"
        "image_input" -> "vision"
        "transcription", "transcribe" -> "audio_input"
        "speech", "tts" -> "audio_output"
        "image_output" -> "image_generation"
        "embeddings" -> "embedding"
        "tool_calling", "function_calling" -> "tools"
        else -> key
    }

/** Resolves a targeted override before provider evidence; omitted partial evidence is unknown. */
fun capabilitySupport(
    capability: String,
    reported: Set<String>,
    complete: Boolean,
    overrides: Map<String, Boolean> = emptyMap(),
): CapabilitySupport {
    val key = capabilityKey(capability)
    val override = overrides.entries.firstOrNull { capabilityKey(it.key) == key }?.value
    return when {
        override == true -> CapabilitySupport.SUPPORTED
        override == false -> CapabilitySupport.UNSUPPORTED
        reported.any { capabilityKey(it) == key } -> CapabilitySupport.SUPPORTED
        complete -> CapabilitySupport.UNSUPPORTED
        else -> CapabilitySupport.UNKNOWN
    }
}
