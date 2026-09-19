package de.heckenmann.visualagent.update

/** Small SemVer 2.0-compatible comparator used for release selection. */
internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
            .takeIf { it != 0 }
            ?.let { return it }
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1
        return comparePrerelease(other)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + prerelease.takeIf(List<String>::isNotEmpty)?.joinToString(prefix = "-", separator = ".").orEmpty()

    private fun comparePrerelease(other: SemanticVersion): Int {
        val size = minOf(prerelease.size, other.prerelease.size)
        repeat(size) { index ->
            val left = prerelease[index]
            val right = other.prerelease[index]
            if (left == right) return@repeat
            val numericComparison = compareNumericIdentifiers(left, right)
            if (numericComparison != null) return numericComparison
            return left.compareTo(right)
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    private fun compareNumericIdentifiers(
        left: String,
        right: String,
    ): Int? {
        val leftNumeric = left.toIntOrNull()
        val rightNumeric = right.toIntOrNull()
        return when {
            leftNumeric != null && rightNumeric != null -> leftNumeric.compareTo(rightNumeric)
            leftNumeric != null -> -1
            rightNumeric != null -> 1
            else -> null
        }
    }

    companion object {
        private val VERSION_PATTERN = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$")

        /** Parse a release tag or version string, returning null for unsupported versions. */
        fun parse(raw: String): SemanticVersion? {
            val value = raw.trim().substringBefore('+')
            val match = VERSION_PATTERN.matchEntire(value) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
                prerelease =
                    match.groupValues[4]
                        .takeIf(String::isNotBlank)
                        ?.split('.')
                        .orEmpty(),
            )
        }
    }
}
