package de.heckenmann.visualagent.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubAgentsFxmlLayoutTest {
    @Test
    fun `agents panel exposes workload summary and empty state`() {
        val resource =
            javaClass
                .getResourceAsStream("/fxml/sub-agents-panel.fxml")
                ?.bufferedReader()
                ?.use { it.readText() }

        assertTrue(
            resource != null &&
                resource.contains("fx:id=\"agentCountLabel\"") &&
                resource.contains("fx:id=\"activeJobsLabel\"") &&
                resource.contains("fx:id=\"emptyState\"") &&
                resource.contains("fx:id=\"emptyCreateAgentButton\""),
            "sub-agents-panel.fxml must expose summary counters and an actionable empty state",
        )
    }
}
