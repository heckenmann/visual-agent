package de.heckenmann.visualagent.workspace

import java.io.IOException

/** Preserves the download boundary's safe failure reporting independently of transfer orchestration. */
internal fun safeDownloadFailure(error: Exception): String =
    when (error) {
        is IllegalArgumentException -> error.message ?: "The remote source or destination is invalid"
        is IOException -> error.message ?: "The remote transfer could not be completed"
        else -> "The remote transfer could not be completed"
    }.replace(Regex("(?i)(password|token|secret|api[_-]?key)\\s*[=:]\\s*[^, ]+"), "credential=[redacted]")

/** Rejects credential-bearing source queries before starting a remote transfer. */
internal fun hasDownloadCredentialQuery(rawQuery: String?): Boolean =
    rawQuery?.split('&', ';')?.any { parameter -> credentialQueryKey.containsMatchIn(parameter.substringBefore('=')) } ?: false

private val credentialQueryKey = Regex("(?i)(password|token|secret|api[_-]?key|credential|signature)")
