@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.components

import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Lint-style test that verifies no hardcoded `Color(0x...)` literals remain in
 * the Compose UI production source.
 *
 * Theme definitions in [ComposeWorkspaceTheme] are allowed because they are the
 * single source of truth for the Material3 color schemes.
 */
class ComposeHardcodedColorTest {
    private val composeDir =
        listOf(
            File("src/main/kotlin/de/heckenmann/visualagent/ui"),
            File("modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui"),
        ).first { it.isDirectory }
    private val themeFileName = "ComposeWorkspaceTheme.kt"
    private val colorPattern = Regex("""Color\s*\(\s*0x""")

    @Test
    fun `no hardcoded Color literals remain in compose production files except theme`() {
        require(composeDir.isDirectory) { "Compose source directory not found: ${composeDir.absolutePath}" }

        val violations = mutableListOf<String>()
        composeDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != themeFileName }
            .forEach { file ->
                file.readText().lineSequence().forEachIndexed { index, line ->
                    if (colorPattern.containsMatchIn(line)) {
                        violations += "${file.name}:${index + 1}"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail("Hardcoded Color literals found:\n${violations.joinToString("\n")}")
        }
    }

    @Test
    fun `theme file defines light and dark color schemes`() {
        val themeFile =
            requireNotNull(composeDir.walkTopDown().firstOrNull { it.isFile && it.name == themeFileName }) {
                "Theme file not found below ${composeDir.absolutePath}"
            }

        val source = themeFile.readText()
        assertEquals(true, source.contains("fun visualAgentLightColorScheme"), "Missing light scheme")
        assertEquals(true, source.contains("fun visualAgentDarkColorScheme"), "Missing dark scheme")
    }
}
