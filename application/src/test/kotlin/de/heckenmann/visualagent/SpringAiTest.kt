package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@SpringBootTest(properties = ["visual-agent.ui.enabled=false", "visual-agent.db.path=jdbc:h2:mem:test"])
class SpringAiTest {
    @Autowired
    private lateinit var chatModel: ChatModel

    @Autowired
    private lateinit var settings: SettingsPort

    @Autowired
    private lateinit var preferences: PreferenceStore

    @Autowired
    private lateinit var workspaceFiles: WorkspaceFileService

    @Test
    fun contextLoads() {
        assertNotNull(chatModel)
    }

    @Test
    fun `settings save persists conversation settings in the database`() {
        val original = settings.snapshot()
        val changed =
            original.copy(
                userModelInstruction = "Persisted integration test value",
                contextLength = 8192,
                loadLimit = 125,
                maxParallelSubAgents = 7,
                queueFlushMode = "ALL",
            )

        try {
            settings.save(changed)

            assertEquals(
                changed.userModelInstruction,
                preferences.getPreference(AppConfigBean.KEY_SESSION_USER_MODEL_INSTRUCTION),
            )
            assertEquals(changed.contextLength.toString(), preferences.getPreference(AppConfigBean.KEY_SESSION_CONTEXT_LENGTH))
            assertEquals(changed.loadLimit.toString(), preferences.getPreference(AppConfigBean.KEY_SESSION_LOAD_LIMIT))
            assertEquals(
                changed.maxParallelSubAgents.toString(),
                preferences.getPreference(AppConfigBean.KEY_SESSION_MAX_PARALLEL_SUB_AGENTS),
            )
            assertEquals(changed.queueFlushMode, preferences.getPreference(AppConfigBean.KEY_SESSION_QUEUE_FLUSH_MODE))
        } finally {
            settings.save(original)
        }
    }

    @Test
    fun `workspace delete operations reach their implementation with the reactive transaction manager`() {
        assertFalse(workspaceFiles.deleteFile("missing-workspace-file"))
        assertFailsWith<IllegalArgumentException> { workspaceFiles.deleteDirectory("", recursive = true) }
    }
}
