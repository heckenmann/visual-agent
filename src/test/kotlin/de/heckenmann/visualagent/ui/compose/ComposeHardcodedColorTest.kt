package de.heckenmann.visualagent.ui.compose

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
    private val composeDir = File("src/main/kotlin/de/heckenmann/visualagent/ui/compose")
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
        val themeFile = File(composeDir, themeFileName)
        require(themeFile.isFile) { "Theme file not found: ${themeFile.absolutePath}" }

        val source = themeFile.readText()
        assertEquals(true, source.contains("fun visualAgentLightColorScheme"), "Missing light scheme")
        assertEquals(true, source.contains("fun visualAgentDarkColorScheme"), "Missing dark scheme")
    }
}
