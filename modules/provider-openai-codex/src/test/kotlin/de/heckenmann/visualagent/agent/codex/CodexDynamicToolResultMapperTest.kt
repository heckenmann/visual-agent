package de.heckenmann.visualagent.agent.codex

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Verifies trusted media conversion and structured dynamic-tool failure handling. */
class CodexDynamicToolResultMapperTest {
    @Test
    fun `dynamic tool media results map images and reserve audio content items`() {
        val imagePayload =
            """{"content":"{\"path\":\"diagram.png\",\"mimeType\":\"image/png\",\"base64\":\"AQI=\"}"}"""
        val imageItems =
            CodexDynamicToolResultMapper.contentItems(
                imagePayload,
                allowInlineImage = true,
            )
        val untrustedImageItems = CodexDynamicToolResultMapper.contentItems(imagePayload)
        val audioItems =
            CodexDynamicToolResultMapper.contentItems(
                """{"content":"{\"mimeType\":\"audio/wav\",\"base64\":\"AQI=\"}"}""",
            )
        val futureAudioItems =
            CodexDynamicToolResultMapper.contentItems(
                """{"content":"{\"mimeType\":\"audio/wav\",\"base64\":\"AQI=\"}"}""",
                allowInlineAudio = true,
            )
        val failureResponse =
            CodexDynamicToolResultMapper.response(
                """{"success":false,"content":"","error":"workspace timeout"}""",
                allowInlineImage = false,
            )
        val imageType = content(imageItems[0].jsonObject, "type")
        val imageMediaType = content(imageItems[1].jsonObject, "type")
        val imageUrl = content(imageItems[1].jsonObject, "imageUrl")
        val untrustedImageType = content(untrustedImageItems.single().jsonObject, "type")
        val audioType = content(audioItems.single().jsonObject, "type")
        val audioUrl = content(audioItems.single().jsonObject, "audioUrl")
        val futureAudioType = content(futureAudioItems.single().jsonObject, "type")

        assertEquals("inputText", imageType)
        assertEquals("inputImage", imageMediaType)
        assertEquals("data:image/png;base64,AQI=", imageUrl)
        assertEquals("inputText", untrustedImageType)
        assertEquals("inputText", audioType)
        assertEquals(null, audioUrl)
        assertEquals("inputAudio", futureAudioType)
        assertFalse(failureResponse["success"]?.jsonPrimitive?.booleanOrNull ?: true)
        assertEquals(
            "workspace timeout",
            failureResponse["contentItems"]
                ?.jsonArray
                ?.single()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content,
        )
    }

    private fun content(
        item: JsonObject,
        field: String,
    ): String? = item[field]?.jsonPrimitive?.content
}
