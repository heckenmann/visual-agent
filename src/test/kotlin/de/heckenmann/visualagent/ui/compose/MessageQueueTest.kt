package de.heckenmann.visualagent.ui.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageQueueTest {
    @Test
    fun `enqueue adds message and returns id`() {
        val queue = MessageQueue()
        val id = queue.enqueue("Hello", QueuedMessageSource.USER)
        assertEquals(1, queue.size)
        assertTrue(queue.isNotEmpty)
        assertNotNull(queue.peek())
        assertEquals("Hello", queue.peek()!!.content)
        assertEquals(QueuedMessageSource.USER, queue.peek()!!.source)
    }

    @Test
    fun `dequeue removes and returns first message`() {
        val queue = MessageQueue()
        queue.enqueue("First", QueuedMessageSource.USER)
        queue.enqueue("Second", QueuedMessageSource.TODO_RETURN)
        val first = queue.dequeue()
        assertEquals("First", first?.content)
        assertEquals(1, queue.size)
        val second = queue.dequeue()
        assertEquals("Second", second?.content)
        assertEquals(0, queue.size)
        assertNull(queue.dequeue())
    }

    @Test
    fun `remove deletes message by id`() {
        val queue = MessageQueue()
        val id = queue.enqueue("Remove me", QueuedMessageSource.USER)
        queue.enqueue("Keep me", QueuedMessageSource.USER)
        assertTrue(queue.remove(id))
        assertEquals(1, queue.size)
        assertEquals("Keep me", queue.peek()!!.content)
    }

    @Test
    fun `clear removes all messages`() {
        val queue = MessageQueue()
        queue.enqueue("A", QueuedMessageSource.USER)
        queue.enqueue("B", QueuedMessageSource.TODO_RETURN)
        queue.clear()
        assertEquals(0, queue.size)
        assertFalse(queue.isNotEmpty)
    }

    @Test
    fun `default flush mode is ONE_BY_ONE`() {
        val queue = MessageQueue()
        assertEquals(QueueFlushMode.ONE_BY_ONE, queue.flushMode)
    }

    @Test
    fun `queued message has correct fields`() {
        val msg =
            QueuedMessage(
                content = "Task done",
                source = QueuedMessageSource.TODO_RETURN,
                todoId = "todo-1",
                agentId = "agent-2",
            )
        assertEquals("Task done", msg.content)
        assertEquals(QueuedMessageSource.TODO_RETURN, msg.source)
        assertEquals("todo-1", msg.todoId)
        assertEquals("agent-2", msg.agentId)
        assertNotNull(msg.id)
        assertNotNull(msg.queuedAt)
    }

    @Test
    fun `flushing flag prevents concurrent flush`() {
        val queue = MessageQueue()
        assertFalse(queue.flushing)
        queue.flushing = true
        assertTrue(queue.flushing)
    }
}
