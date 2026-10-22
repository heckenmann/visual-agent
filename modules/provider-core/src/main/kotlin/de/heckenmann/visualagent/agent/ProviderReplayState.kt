package de.heckenmann.visualagent.agent

import kotlinx.serialization.Serializable

/** Provider-owned replay data, kept separate from display text, diagnostics, and telemetry. */
@Serializable
data class ProviderReplayState(
    val format: String,
    val providerProfileId: String,
    val modelId: String,
    val partsJson: String,
) {
    override fun toString(): String = "ProviderReplayState(redacted)"
}
