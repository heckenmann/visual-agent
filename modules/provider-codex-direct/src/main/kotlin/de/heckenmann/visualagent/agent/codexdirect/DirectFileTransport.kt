package de.heckenmann.visualagent.agent.codexdirect

import com.openai.core.jsonMapper
import com.openai.models.responses.EasyInputMessage
import com.openai.models.responses.ResponseInputContent
import com.openai.models.responses.ResponseInputFile
import com.openai.models.responses.ResponseInputImage
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseInputText
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.messages.part.UnknownPart
import org.springframework.ai.openai.responses.OpenAiResponsesMetadata
import java.util.Base64

/** Preserves real filenames where Spring AI M1 otherwise appends MIME subtypes such as `.plain`. */
internal fun directFileTransportMessage(message: Message): Message {
    if (message !is UserMessage || message.media.isEmpty() || message.media.all { it.mimeType.type == "image" }) return message
    val content = mutableListOf<ResponseInputContent>()
    message.text?.takeIf(String::isNotBlank)?.let {
        content += ResponseInputContent.ofInputText(ResponseInputText.builder().text(it).build())
    }
    message.media.forEach { file ->
        validateDirectFile(file)
        val data = "data:${file.mimeType};base64," + Base64.getEncoder().encodeToString(file.data as ByteArray)
        content +=
            if (file.mimeType.type == "image") {
                ResponseInputContent.ofInputImage(
                    ResponseInputImage
                        .builder()
                        .detail(ResponseInputImage.Detail.AUTO)
                        .imageUrl(data)
                        .build(),
                )
            } else {
                ResponseInputContent.ofInputFile(
                    ResponseInputFile
                        .builder()
                        .filename(file.name)
                        .fileData(data)
                        .build(),
                )
            }
    }
    val item =
        ResponseInputItem.ofEasyInputMessage(
            EasyInputMessage
                .builder()
                .role(EasyInputMessage.Role.USER)
                .contentOfResponseInputMessageContentList(content)
                .build(),
        )
    return AssistantMessage
        .builder()
        .properties(message.metadata)
        .parts(listOf(UnknownPart(OpenAiResponsesMetadata.PROVIDER, "message", jsonMapper().writeValueAsString(item), null, emptyMap())))
        .build()
}
