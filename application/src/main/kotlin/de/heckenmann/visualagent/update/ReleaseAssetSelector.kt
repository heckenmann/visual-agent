package de.heckenmann.visualagent.update

/** Asset selection result for one operating-system and package preference. */
internal data class ReleaseAssetSelection(
    val selected: GitHubReleaseAsset?,
    val matchingAssets: List<GitHubReleaseAsset>,
)

/** Selects assets using the names produced by the repository release workflow. */
internal object ReleaseAssetSelector {
    fun select(
        osName: String,
        architecture: String,
        packageType: String?,
        assets: List<GitHubReleaseAsset>,
    ): ReleaseAssetSelection {
        val expectedNames = expectedNames(osName, architecture, packageType)
        val matching = expectedNames.mapNotNull { expected -> assets.firstOrNull { it.name == expected } }
        return ReleaseAssetSelection(matching.singleOrNull(), matching)
    }

    private fun expectedNames(
        osName: String,
        architecture: String,
        packageType: String?,
    ): List<String> {
        val normalizedPackage = packageType?.trim()?.lowercase()?.takeIf(String::isNotBlank)
        val platform = osName.lowercase()
        val arch = normalizeArchitecture(architecture)
        return when {
            platform.contains("win") -> windowsNames(normalizedPackage)
            platform.contains("mac") || platform.contains("darwin") -> macosNames(normalizedPackage, arch)
            platform.contains("linux") -> linuxNames(normalizedPackage)
            else -> throw IllegalArgumentException("Unsupported update platform: $osName")
        }
    }

    private fun windowsNames(packageType: String?): List<String> =
        when (packageType) {
            null -> listOf("visual-agent-windows.msi", "visual-agent-windows-x64-jar.jar")
            "windows-msi" -> listOf("visual-agent-windows.msi")
            "windows-jar", "platform-jar" -> listOf("visual-agent-windows-x64-jar.jar")
            else -> throw IllegalArgumentException("Unsupported Windows package type: $packageType")
        }

    private fun macosNames(
        packageType: String?,
        architecture: String,
    ): List<String> {
        val suffix = if (architecture == "arm64") "arm64" else "x64"
        return when (packageType) {
            null, "macos-dmg" -> listOf("visual-agent-macos-$suffix.dmg")
            "macos-jar", "platform-jar" -> listOf("visual-agent-macos-$suffix-jar.jar")
            else -> throw IllegalArgumentException("Unsupported macOS package type: $packageType")
        }
    }

    private fun linuxNames(packageType: String?): List<String> =
        when (packageType) {
            null -> listOf("visual-agent-linux-deb.deb", "visual-agent-linux-rpm.rpm", "visual-agent-linux-appimage.AppImage")
            "linux-deb" -> listOf("visual-agent-linux-deb.deb")
            "linux-rpm" -> listOf("visual-agent-linux-rpm.rpm")
            "linux-appimage" -> listOf("visual-agent-linux-appimage.AppImage")
            "linux-jar", "platform-jar" -> listOf("visual-agent-linux-x64-jar.jar")
            else -> throw IllegalArgumentException("Unsupported Linux package type: $packageType")
        }

    private fun normalizeArchitecture(value: String): String =
        when (value.lowercase()) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64", "x64" -> "x64"
            else -> value.lowercase()
        }
}
