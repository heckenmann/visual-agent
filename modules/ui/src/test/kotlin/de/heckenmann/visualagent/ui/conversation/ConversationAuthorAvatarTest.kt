@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/** Verifies role avatars share neutral colors while retaining role-specific icons. */
class ConversationAuthorAvatarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `user and assistant avatars use the same neutral theme background`() {
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    avatarGroup("question", "user")
                    avatarGroup("answer", "assistant")
                }
            }
        }

        val avatars = composeTestRule.onAllNodesWithTag("conversation-author-avatar")
        val userAvatar = avatars[0].captureToImage().toPixelMap()
        val assistantAvatar = avatars[1].captureToImage().toPixelMap()
        val sampleX = userAvatar.width / 2
        val sampleY = userAvatar.height / 8
        assertEquals(userAvatar[sampleX, sampleY], assistantAvatar[sampleX, sampleY])
    }

    @Composable
    private fun avatarGroup(
        content: String,
        role: String,
    ) {
        ConversationMessageGroupRow(
            group = ConversationMessageGroup(listOf(ConversationTimelineItem.Persisted(Message(role, content, id = content), 0))),
            sending = false,
            deletingMessageIds = emptySet(),
            onDeleteMessage = {},
            onStatusChange = {},
            onEditMessage = {},
            onRetry = {},
        )
    }
}
