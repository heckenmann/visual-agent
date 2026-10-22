package de.heckenmann.visualagent.agent.codexdirect

import org.springframework.ai.content.Media

/** Rejects unsupported or unbounded payloads before credentials or a transport are acquired. */
internal fun validateDirectFile(file: Media) {
    require(file.mimeType.type !in setOf("audio", "video")) { "Direct Codex does not support audio or video input" }
    val bytes = file.data as? ByteArray ?: error("Native file input requires server-owned inline bytes")
    require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024) { "Native file input must contain between 1 byte and 8 MiB" }
    require(file.name.isNotBlank() && '/' !in file.name && '\\' !in file.name) { "Native file input requires a simple filename" }
}
