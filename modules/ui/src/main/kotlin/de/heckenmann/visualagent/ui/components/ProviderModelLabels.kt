package de.heckenmann.visualagent.ui.components

import de.heckenmann.visualagent.protocol.ProviderModel

/** Keep legacy plain model names when a provider has not reported capabilities. */
internal fun ProviderModel.labelWithCapabilities(): String =
    capabilities
        .toMutableSet()
        .apply {
            capabilityOverrides.forEach { (capability, supported) ->
                if (supported) add(capability) else remove(capability)
            }
        }.sorted()
        .let { labels ->
            if (labels.isEmpty()) {
                name
            } else {
                "$name [${labels.joinToString(", ")}]"
            }
        }
