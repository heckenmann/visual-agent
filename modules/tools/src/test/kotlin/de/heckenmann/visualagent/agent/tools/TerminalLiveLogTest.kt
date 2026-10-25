package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.model.ToolLiveState
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalLiveLogTest {
    @Test
    fun `short lines retain all 750 characters across 512 character reads and runtime bounds`() {
        val lines = List(30) { "Line-${it.toString().padStart(2, '0')}-abcdefghijklmnop" }
        val text = lines.joinToString("\n", postfix = "\n")
        val log = TerminalLiveLog()
        val session = ToolLiveSession(publish = {})
        StringReader(text).use { reader ->
            val buffer = CharArray(512)
            var count = reader.read(buffer)
            while (count >= 0) {
                log.append(buffer, count)
                session.report(ToolLiveState("", 0, "Running", logs = log.snapshot()))
                count = reader.read(buffer)
            }
        }
        val retained = session.finish()!!.logs
        assertEquals(750, text.length)
        assertEquals(lines, retained)
        assertEquals(text, retained.joinToString("\n", postfix = "\n"))
    }

    @Test
    fun `partial lines replace their previous partial value and CRLF can span reads`() {
        val log = TerminalLiveLog()
        append(log, "Fir")
        assertEquals(listOf("Fir"), log.snapshot())
        append(log, "st\r")
        assertEquals(listOf("First"), log.snapshot())
        append(log, "\nSecond\n\nThird")
        assertEquals(listOf("First", "Second", "", "Third"), log.snapshot())
    }

    @Test
    fun `retention limits truncate only long logical lines and retain subsequent output`() {
        val log = TerminalLiveLog()
        append(log, "x".repeat(1000) + "\nNext")
        assertEquals(listOf("x".repeat(500), "Next"), log.snapshot())
        append(log, "\n" + List(50) { "Entry $it" }.joinToString("\n", postfix = "\n"))
        assertEquals(List(40) { "Entry ${it + 10}" }, log.snapshot())
    }

    private fun append(
        log: TerminalLiveLog,
        text: String,
    ) = log.append(text.toCharArray(), text.length)
}
