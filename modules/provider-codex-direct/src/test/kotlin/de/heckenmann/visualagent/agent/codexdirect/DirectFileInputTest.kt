package de.heckenmann.visualagent.agent.codexdirect

import com.openai.client.OpenAIClient
import com.openai.core.RequestOptions
import com.openai.core.jsonMapper
import com.openai.models.responses.ResponseCreateParams
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.Media
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeType
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DirectFileInputTest {
    @Test
    fun `both authentication routes preserve filenames and inline bytes in native input file items`() {
        val bytes = "synthetic file".toByteArray()
        listOf("application/pdf" to "report.pdf", "text/plain" to "notes.txt", "text/csv" to "table.csv").forEach { (mime, name) ->
            val file =
                Media
                    .builder()
                    .mimeType(MimeType.valueOf(mime))
                    .data(ByteArrayResource(bytes))
                    .name(name)
                    .build()
            validateDirectFile(file)
            DirectProviderAuthMode.entries.forEach { mode ->
                val captured = slot<ResponseCreateParams>()
                val client = mockk<OpenAIClient>(relaxed = true)
                every { client.responses().create(capture(captured), any<RequestOptions>()) } throws IllegalStateException("captured")
                val model =
                    OpenAiResponsesChatModel
                        .builder()
                        .openAiClient(client)
                        .openAiClientAsync(mockk())
                        .build()
                val options = directResponsesOptions(ChatRequestContext(emptyList(), model = "selected"), mode, emptyList())
                val prompt =
                    Prompt(
                        listOf(
                            UserMessage
                                .builder()
                                .text("summarize")
                                .media(file)
                                .build(),
                        ),
                        options,
                    )

                assertFailsWith<IllegalStateException> { DirectResponsesTransport(model, mode).call(prompt) }

                val body = Json.parseToJsonElement(jsonMapper().writeValueAsString(captured.captured._body())).jsonObject
                val content =
                    body
                        .getValue("input")
                        .jsonArray
                        .single()
                        .jsonObject
                        .getValue("content")
                        .jsonArray
                val native = content.last().jsonObject
                assertEquals("input_file", native.getValue("type").jsonPrimitive.content)
                assertEquals(name, native.getValue("filename").jsonPrimitive.content)
                assertEquals(
                    "data:$mime;base64," +
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(bytes),
                    native.getValue("file_data").jsonPrimitive.content,
                )
            }
        }
    }

    @Test
    fun `audio video remote URLs empty and oversized files are rejected before sending`() {
        assertFailsWith<IllegalArgumentException> { validateDirectFile(media(byteArrayOf(1), "audio/wav")) }
        assertFailsWith<IllegalArgumentException> { validateDirectFile(media(byteArrayOf(1), "video/mp4")) }
        assertFailsWith<IllegalArgumentException> { validateDirectFile(media(byteArrayOf(), "application/pdf")) }
        assertFailsWith<IllegalArgumentException> { validateDirectFile(media(ByteArray(8 * 1024 * 1024 + 1), "application/pdf")) }
        val remote =
            Media
                .builder()
                .mimeType(MimeType.valueOf("application/pdf"))
                .data(URI("https://example.test/private.pdf"))
                .build()
        assertFailsWith<IllegalStateException> { validateDirectFile(remote) }
    }

    @Test
    fun `inline file names cannot contain host paths`() {
        val file =
            Media
                .builder()
                .mimeType(
                    MimeType.valueOf("application/pdf"),
                ).data(ByteArrayResource(byteArrayOf(1)))
                .name("/private/file.pdf")
                .build()
        assertFailsWith<IllegalArgumentException> { validateDirectFile(file) }
    }

    private fun media(
        bytes: ByteArray,
        mime: String,
    ): Media =
        Media
            .builder()
            .mimeType(MimeType.valueOf(mime))
            .data(ByteArrayResource(bytes))
            .name("report.pdf")
            .build()
}
