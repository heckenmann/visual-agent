package de.heckenmann.visualagent.ui.info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.OpenSourceLicenseDocument
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** The license viewer filters documents and shows the selected full text. */
class OpenSourceLicensesPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shows project license before third party licenses`() {
        val ordered =
            orderProjectLicenseFirst(
                listOf(
                    OpenSourceLicenseDocument("org.jetbrains.skiko:skiko-awt:0.150.2", "skiko/LICENSE", "Skiko license"),
                    OpenSourceLicenseDocument("Visual Agent", "LICENSE", "Project license"),
                ),
            )

        assertEquals(listOf("LICENSE", "skiko/LICENSE"), ordered.map { it.path })
    }

    @Test
    fun `filters bundled documents and shows selected text`() {
        val documents =
            listOf(
                OpenSourceLicenseDocument("Visual Agent", "LICENSE", "Project MIT license text"),
                OpenSourceLicenseDocument("org.jetbrains.skiko:skiko-awt:0.150.2", "skiko/LICENSE", "Skiko Apache license text"),
            )
        val selected = mutableStateOf<String?>("LICENSE")
        val query = mutableStateOf("")
        compose.setContent {
            MaterialTheme {
                OpenSourceLicensesContent(
                    documents = documents,
                    selectedPath = selected.value,
                    search = query.value,
                    onSearchChange = { query.value = it },
                    onSelect = { selected.value = it },
                )
            }
        }

        compose.onNodeWithText("Project MIT license text").assertExists()
        compose.onNodeWithTag("license-filter").performTextInput("skiko")
        compose.onNodeWithTag("license-document-LICENSE").assertDoesNotExist()
        compose.onNodeWithText("Skiko Apache license text").assertExists()
        compose.onNodeWithTag("license-document-skiko/LICENSE").performClick()
        compose.onNodeWithText("Skiko Apache license text").assertExists()
    }

    @Test
    fun `long component heading cannot displace license text in a narrow viewer`() {
        val longComponent =
            "org.graalvm.truffle:truffle-api:25.4.4.1.1, org.graalvm.sdk:nativeimage:25.4.4.1.1, " +
                "org.graalvm.shadowed:xz:25.4.4.1.1, org.graalvm.truffle:truffle-runtime:25.4.4.1.1"
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).height(240.dp)) {
                    OpenSourceLicensesContent(
                        documents = listOf(OpenSourceLicenseDocument(longComponent, "license.txt", "License body")),
                        selectedPath = "license.txt",
                        search = "",
                        onSearchChange = {},
                        onSelect = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("license-document-heading").assertIsDisplayed()
        compose.onNodeWithTag("license-document-text").assertIsDisplayed().assertHeightIsAtLeast(1.dp)
    }
}
