package de.heckenmann.visualagent.ui.info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.protocol.ApplicationInformation
import de.heckenmann.visualagent.protocol.ApplicationInformationPort
import de.heckenmann.visualagent.protocol.OpenSourceLicenseDocument
import de.heckenmann.visualagent.protocol.OpenSourceLicensePort
import de.heckenmann.visualagent.protocol.ProjectMetadata
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Verifies the About panel exposes runtime metadata, canonical project links, and legal documents. */
class AboutPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shows packaged version runtime and project links`() {
        val openedUrls = mutableListOf<String>()
        val uriHandler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    openedUrls += uri
                }
            }

        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                MaterialTheme {
                    AboutPanel(applicationInformationPort(), licensePort())
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(AppIdentity.DISPLAY_NAME).assertExists()
        compose.onNodeWithText("Version 1.2.3-beta.1").assertExists()
        compose.onNodeWithText("OpenJDK Runtime 27.0.1\nmacOS 15.0 · aarch64").assertExists()
        compose.onNodeWithText(ProjectMetadata.DESCRIPTION).assertExists()
        compose.onNodeWithText("Repository").assertExists()
        compose.onNodeWithText("Issue tracker").assertExists()
        compose.onNodeWithText("Releases and changelog").assertExists()
        compose.onNodeWithText("README and documentation").assertExists()
        compose.onNodeWithText("Code of Conduct").assertExists()
        compose.onNodeWithText("Contributing guide").assertDoesNotExist()
        compose.onNodeWithText("Security policy").assertDoesNotExist()

        val resources = ProjectMetadata.projectLinks() + ProjectMetadata.communityLinks()
        resources.forEach { resource ->
            compose.onNodeWithContentDescription("Open ${resource.title}").performClick()
        }

        assertEquals(resources.map { it.url }, openedUrls)
    }

    @Test
    fun `project license action opens the bundled license tab`() {
        compose.setContent {
            MaterialTheme {
                AboutPanel(applicationInformationPort(), licensePort())
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("View Visual Agent license").performClick()
        compose.onNodeWithText("Filter licenses").assertExists()
        compose.onNodeWithText("Project MIT license text").assertExists()
    }

    @Test
    fun `overview renders in light and dark themes`() {
        compose.setContent {
            Column {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    Box(Modifier.width(620.dp).height(420.dp)) {
                        AboutPanel(applicationInformationPort(), licensePort())
                    }
                }
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.width(620.dp).height(420.dp)) {
                        AboutPanel(applicationInformationPort(), licensePort())
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.onAllNodesWithText("Version 1.2.3-beta.1").assertCountEquals(2)
    }

    @Test
    fun `project license is exposed only through the bundled document view`() {
        val documents = licensePort().documents()

        assertEquals("LICENSE", documents.first().path)
        assertFalse(documents.first().text.isBlank())
    }

    private fun applicationInformationPort(): ApplicationInformationPort =
        object : ApplicationInformationPort {
            override fun current(): ApplicationInformation =
                ApplicationInformation(
                    version = "1.2.3-beta.1",
                    runtimeName = "OpenJDK Runtime",
                    runtimeVersion = "27.0.1",
                    operatingSystem = "macOS",
                    operatingSystemVersion = "15.0",
                    architecture = "aarch64",
                )
        }

    private fun licensePort(): OpenSourceLicensePort =
        object : OpenSourceLicensePort {
            override fun documents(): List<OpenSourceLicenseDocument> =
                listOf(OpenSourceLicenseDocument("Visual Agent", "LICENSE", "Project MIT license text"))
        }
}
