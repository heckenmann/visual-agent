package de.heckenmann.visualagent.protocol

import de.heckenmann.visualagent.model.capabilitySupport

/** Protocol-visible evidence state, shared with the pure model domain. */
typealias CapabilitySupport = de.heckenmann.visualagent.model.CapabilitySupport

/** Canonical capability key used when staging a targeted manual override. */
fun capabilityKey(value: String): String =
    de.heckenmann.visualagent.model
        .capabilityKey(value)

/** Resolves model features with the same evidence semantics as the server. */
fun ProviderModel.capabilitySupport(capability: String): CapabilitySupport =
    capabilitySupport(capability, capabilities, capabilitiesComplete, capabilityOverrides)
