package de.heckenmann.visualagent.protocol

/** Maximum draft or result length accepted by prompt interpolation. */
const val MAX_INTERPOLATION_CHARACTERS: Int = 16_384

/** Rejects blank or oversized text without changing the user's draft. */
fun validateInterpolationDraft(draft: String) {
    require(draft.isNotBlank()) { "The prompt must not be blank" }
    require(draft.length <= MAX_INTERPOLATION_CHARACTERS) { "The prompt is too long to interpolate" }
}
