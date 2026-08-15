package de.heckenmann.visualagent.protocol

/** Parses RGB or ARGB hexadecimal text into a packed ARGB color value. */
fun parseHexColor(hex: String): Int? {
    if (hex.isEmpty()) return null
    val stripped = hex.removePrefix("#")
    val value = stripped.toLongOrNull(16) ?: return null
    return when (stripped.length) {
        6 -> (0xFF000000L or value).toInt()
        8 -> value.toInt()
        else -> null
    }
}
