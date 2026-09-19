package de.heckenmann.visualagent.update

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseAssetSelectorTest {
    private val assets =
        listOf(
            GitHubReleaseAsset("visual-agent-linux-deb.deb", 10, "sha256:deb"),
            GitHubReleaseAsset("visual-agent-linux-rpm.rpm", 20, "sha256:rpm"),
            GitHubReleaseAsset("visual-agent-linux-appimage.AppImage", 30, "sha256:appimage"),
            GitHubReleaseAsset("visual-agent-macos-arm64.dmg", 40, "sha256:mac"),
            GitHubReleaseAsset("visual-agent-windows.msi", 50, "sha256:windows-msi"),
            GitHubReleaseAsset("visual-agent-windows-x64-jar.jar", 60, "sha256:windows-jar"),
        )

    @Test
    fun `selects an exact package asset`() {
        val selection = ReleaseAssetSelector.select("Linux", "x86_64", "linux-appimage", assets)

        assertEquals("visual-agent-linux-appimage.AppImage", selection.selected?.name)
        assertEquals(1, selection.matchingAssets.size)
    }

    @Test
    fun `reports linux candidates without selecting an ambiguous package`() {
        val selection = ReleaseAssetSelector.select("Linux", "x86_64", null, assets)

        assertNull(selection.selected)
        assertEquals(3, selection.matchingAssets.size)
    }

    @Test
    fun `maps arm macos package names`() {
        val selection = ReleaseAssetSelector.select("Mac OS X", "aarch64", null, assets)

        assertEquals("visual-agent-macos-arm64.dmg", selection.selected?.name)
    }

    @Test
    fun `reports ambiguous windows packages without selecting one`() {
        val selection = ReleaseAssetSelector.select("Windows 11", "amd64", null, assets)

        assertNull(selection.selected)
        assertEquals(2, selection.matchingAssets.size)
    }
}
