package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.UpdateAssetInfo
import de.heckenmann.visualagent.agent.tools.api.UpdateCheckPort
import de.heckenmann.visualagent.agent.tools.api.UpdateCheckResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateCheckToolTest {
    @Test
    fun `returns current version and update status as structured content`() {
        val tool =
            UpdateCheckTool(
                UpdateCheckPort { request ->
                    assertTrue(request.includePrerelease)
                    assertEquals("linux-appimage", request.packageType)
                    UpdateCheckResult(
                        currentVersion = "1.0.0",
                        latestVersion = "1.1.0",
                        updateAvailable = true,
                        channel = "preview",
                        releaseName = "Preview",
                        releaseNotes = "Notes",
                        releaseUrl = "https://github.com/heckenmann/visual-agent/releases/v1.1.0",
                        publishedAt = "2026-09-19T00:00:00Z",
                        selectedAsset = UpdateAssetInfo("visual-agent-linux-appimage.AppImage", 42, "digest"),
                        availableAssets = emptyList(),
                    )
                },
            )

        val result = tool.execute("""{"includePrerelease":true,"packageType":"linux-appimage"}""", emptyMap())
        val output = Json.parseToJsonElement(result.content).jsonObject

        assertTrue(result.success)
        assertEquals("1.0.0", output["currentVersion"]!!.jsonPrimitive.content)
        assertTrue(output["updateAvailable"]!!.jsonPrimitive.content.toBoolean())
    }
}
