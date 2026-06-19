package de.heckenmann.visualagent.ui.panels.tests

import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.ui.panels.FxTestSupport
import de.heckenmann.visualagent.ui.panels.TodoPanel
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.layout.VBox
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodoPanelInteractionTest {
    @Test
    fun `summary filters and bulk actions track manager state`() {
        val manager = TodoManager()
        val pending = manager.add("Pending")
        val active = manager.add("Active")
        manager.updateStatus(active.id, TodoStatus.IN_PROGRESS)
        val completed = manager.add("Completed")
        manager.updateStatus(completed.id, TodoStatus.COMPLETED)
        val cancelled = manager.add("Cancelled")
        manager.updateStatus(cancelled.id, TodoStatus.CANCELLED)

        val panel = FxTestSupport.run { TodoPanel(manager) }
        FxTestSupport.flush()

        val selector =
            FxTestSupport.run {
                assertEquals("4", panel.field<Label>("totalCountLabel").text)
                assertEquals("1", panel.field<Label>("openCountLabel").text)
                assertEquals("1", panel.field<Label>("inProgressCountLabel").text)
                assertEquals("1", panel.field<Label>("doneCountLabel").text)

                panel.field<ComboBox<String>>("statusFilterSelector").also {
                    it.selectionModel.select("Open")
                }
            }
        FxTestSupport.flush()

        FxTestSupport.run {
            val list = panel.field<ListView<*>>("todoListView")
            assertEquals(listOf("Pending"), list.items.map { (it as de.heckenmann.visualagent.todo.Todo).description })
            selector.selectionModel.select("Cancelled")
        }
        FxTestSupport.flush()

        FxTestSupport.run {
            val list = panel.field<ListView<*>>("todoListView")
            assertEquals(1, list.items.size)
            selector.selectionModel.select("All")
        }
        FxTestSupport.flush()

        FxTestSupport.run {
            panel.field<Button>("completeAllButton").fire()
        }
        FxTestSupport.flush()

        FxTestSupport.run {
            assertTrue(manager.getAll().all { it.status == TodoStatus.COMPLETED })
            assertFalse(panel.field<Button>("deleteCompletedButton").isDisable)
            panel.field<Button>("deleteCompletedButton").fire()
        }
        FxTestSupport.flush()

        FxTestSupport.run {
            assertTrue(manager.getAll().isEmpty())
            assertTrue(panel.field<Button>("completeAllButton").isDisable)
            assertTrue(panel.field<Button>("deleteCompletedButton").isDisable)
            assertEquals("0", panel.field<Label>("totalCountLabel").text)
            assertTrue(panel.field<VBox>("todoEmptyState").isVisible)
            assertTrue(panel.field<Button>("emptyAddButton").isVisible)
            assertEquals("No todos yet", panel.field<Label>("todoEmptyTitleLabel").text)
        }
    }

    @Test
    fun `empty state distinguishes empty source from empty filter`() {
        val manager = TodoManager()
        manager.add("Pending")

        val panel = FxTestSupport.run { TodoPanel(manager) }
        FxTestSupport.flush()

        FxTestSupport.run {
            panel.field<ComboBox<String>>("statusFilterSelector").selectionModel.select("Done")
        }
        FxTestSupport.flush()

        FxTestSupport.run {
            assertTrue(panel.field<VBox>("todoEmptyState").isVisible)
            assertFalse(panel.field<Button>("emptyAddButton").isVisible)
            assertEquals("No matching todos", panel.field<Label>("todoEmptyTitleLabel").text)
        }
    }

    @Test
    fun `selection enables edit action and layout fills available size`() =
        FxTestSupport.run {
            val manager = TodoManager()
            manager.add("Editable")
            val panel = TodoPanel(manager)
            val list = panel.field<ListView<de.heckenmann.visualagent.todo.Todo>>("todoListView")
            val edit = panel.field<Button>("editButton")

            assertTrue(edit.isDisable)
            list.selectionModel.selectFirst()
            assertFalse(edit.isDisable)

            panel.resize(800.0, 600.0)
            panel.layout()
            val root = panel.field<javafx.scene.layout.BorderPane>("rootBorderPane")
            assertEquals(800.0, root.width)
            assertEquals(600.0, root.height)
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> TodoPanel.field(name: String): T {
        val field = TodoPanel::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this) as T
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
