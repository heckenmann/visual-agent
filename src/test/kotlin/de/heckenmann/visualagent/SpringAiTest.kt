package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.ai.chat.model.ChatModel
import kotlin.test.assertNotNull

@SpringBootTest(properties = ["visual-agent.ui.enabled=false"])
class SpringAiTest {

    @Autowired
    private lateinit var chatModel: ChatModel

    @Test
    fun contextLoads() {
        assertNotNull(chatModel)
    }
}
