package de.heckenmann.visualagent.ui

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MainWindowBackButtonTest {
    @Test
    fun `shouldShowBack returns false when active is chatPanel`() {
        val chat = Any()
        val active = chat
        assertFalse(MainWindow.shouldShowBack(active, chat))
    }

    @Test
    fun `shouldShowBack returns true when active is not chatPanel`() {
        val chat = Any()
        val active = Any()
        assertTrue(MainWindow.shouldShowBack(active, chat))
    }
}
