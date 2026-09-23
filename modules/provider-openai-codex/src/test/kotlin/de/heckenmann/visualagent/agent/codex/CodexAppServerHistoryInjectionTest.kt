package de.heckenmann.visualagent.agent.codex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals

/** Verifies that Codex receives completed conversation turns as native history messages. */
class CodexAppServerHistoryInjectionTest {
    @Test
    fun `previous assistant answers are injected with native roles before the current turn`() {
        val directory = createTempDirectory("codex-app-server-context-test-")
        try {
            val prompt =
                Prompt(
                    listOf(
                        SystemMessage("system rules"),
                        UserMessage("earlier question"),
                        AssistantMessage("earlier answer"),
                        UserMessage("follow-up question"),
                    ),
                )
            CodexAppServerChatModel(fakeServer(directory), "gpt-test", emptyList(), directory)
                .completeReactive(prompt)
                .block()

            val historyItems = params(directory.resolve("history-inject.json"))["items"]!!.jsonArray
            assertEquals(listOf("user", "assistant"), historyItems.map { it.jsonObject["role"]!!.jsonPrimitive.content })
            assertEquals(
                "earlier answer",
                historyItems[1]
                    .jsonObject["content"]!!
                    .jsonArray
                    .single()
                    .jsonObject["text"]!!
                    .jsonPrimitive.content,
            )
            val turnInputs = params(directory.resolve("turn-start.json"))["input"]!!.jsonArray
            assertEquals(1, turnInputs.size)
            assertEquals(
                "follow-up question",
                turnInputs
                    .single()
                    .jsonObject["text"]!!
                    .jsonPrimitive.content,
            )
        } finally {
            deleteRecursively(directory)
        }
    }

    private fun fakeServer(directory: Path): Path {
        val executable = directory.resolve("codex")
        Files.writeString(
            executable,
            """
            #!/bin/sh
            while IFS= read -r line; do
              request_id=${'$'}(printf '%s' "${'$'}line" | sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p')
              case "${'$'}line" in
                *'"method":"initialize"'*)
                  printf '{"jsonrpc":"2.0","id":%s,"result":{}}\n' "${'$'}request_id"
                  ;;
                *'"method":"thread/start"'*)
                  printf '{"jsonrpc":"2.0","id":%s,"result":{"thread":{"id":"thread-1"}}}\n' "${'$'}request_id"
                  ;;
                *'"method":"thread/inject_items"'*)
                  printf '%s' "${'$'}line" > '${directory.resolve("history-inject.json")}'
                  printf '{"jsonrpc":"2.0","id":%s,"result":{}}\n' "${'$'}request_id"
                  ;;
                *'"method":"turn/start"'*)
                  printf '%s' "${'$'}line" > '${directory.resolve("turn-start.json")}'
                  printf '{"jsonrpc":"2.0","id":%s,"result":{"threadId":"thread-1","turn":{"id":"turn-1"}}}\n' "${'$'}request_id"
                  printf '%s\n' '{"jsonrpc":"2.0","method":"turn/completed","params":{"threadId":"thread-1","turn":{"id":"turn-1","status":"completed"}}}'
                  ;;
              esac
            done
            """.trimIndent() + "\n",
        )
        check(executable.toFile().setExecutable(true))
        return executable
    }

    private fun params(path: Path): JsonObject =
        Json
            .parseToJsonElement(Files.readString(path))
            .jsonObject
            .getValue("params")
            .jsonObject

    private fun deleteRecursively(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
