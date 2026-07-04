@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandCircleDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Conversation panel with message history, streaming input, and todo actions.
 *
 * Use cases: UC-0000002, UC-0000003, UC-0000004, UC-0000045, UC-0000046,
 * UC-0000049, UC-0000071.
 *
 * @param agentManager Source of conversation history and message streaming
 * @param modalRequester Modal requester used for destructive confirmations
 * @param inFlight In-flight state holder for the active stream indicator
 */
@Composable
internal fun ConversationPanel(
    agentManager: AgentManager,
    modalRequester: ComposeModalRequester,
    inFlight: InFlightStateHolder,
    toolEventBus: ToolEventBus,
) {
    val scope = rememberCoroutineScope()
    val inputFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var history by remember { mutableStateOf(agentManager.getHistory()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var sending by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deletingMessageIds by remember { mutableStateOf(setOf<String>()) }
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }
    DisposableEffect(toolEventBus) {
        val handle =
            toolEventBus.addListener { event ->
                if (event.phase == ToolCallPhase.FINISHED) {
                    history = agentManager.getHistory()
                }
            }
        onDispose { handle.close() }
    }
    val sendContent: (String) -> Unit = { rawContent ->
        val content = rawContent.trim()
        if (content.isNotBlank() && !sending) {
            input = ""
            sending = true
            status = "Streaming..."
            history = history + Message("user", content)
            val streamRequestId =
                java.util.UUID
                    .randomUUID()
                    .toString()
            inFlight.markStreamStart(streamRequestId)
            scope.launch {
                val streamedContent = StringBuilder()
                val result =
                    runCatching {
                        agentManager.streamMessage(content) { chunk ->
                            streamedContent.append(chunk)
                            history = history.dropLast(1) + Message("assistant", streamedContent.toString())
                        }
                    }
                result
                    .onSuccess {
                        history = agentManager.getHistory()
                        status = "Ready"
                    }.onFailure {
                        history = agentManager.getHistory()
                        status = "Error: ${it.message}"
                    }.also {
                        inFlight.markStreamEnd(streamRequestId)
                        sending = false
                        inputFocusRequester.requestFocus()
                    }
            }
        }
    }
    val sendCurrentInput = { sendContent(input) }
    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }
    ConversationStartupScrollEffect(history, listState)
    LaunchedEffect(history.size) {
        if (isAtBottom && history.isNotEmpty()) {
            listState.animateScrollToItem(history.lastIndex)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (history.isEmpty()) {
                    item {
                        PanelEmptyState(
                            title = "No conversation yet",
                            body = "Send a message to start the main agent session.",
                        )
                    }
                } else {
                    itemsIndexed(history, key = { index, message -> message.id ?: "temp-$index" }) { index, message ->
                        val previousRole = history.getOrNull(index - 1)?.role
                        val isStreamingPlaceholder =
                            message.role == "assistant" && message.content.isBlank() && sending && index == history.lastIndex
                        val topPadding = if (previousRole == message.role) 2.dp else 10.dp
                        val onDelete: () -> Unit = {
                            message.id?.let { id ->
                                deletingMessageIds += id
                                scope.launch {
                                    delay(DELETE_ANIMATION_DURATION_MS.toLong())
                                    agentManager.deleteMessageById(id)
                                    history = agentManager.getHistory()
                                    deletingMessageIds -= id
                                    status = "Message deleted"
                                }
                            }
                        }
                        when (message.role) {
                            "tool" ->
                                ToolMessageRow(
                                    message = message,
                                    isDeleting = message.id in deletingMessageIds,
                                    onDelete = onDelete,
                                    modifier = Modifier.padding(top = topPadding),
                                )
                            "sub_agent" ->
                                SubAgentMessageRow(
                                    message = message,
                                    isDeleting = message.id in deletingMessageIds,
                                    onDelete = onDelete,
                                    modifier = Modifier.padding(top = topPadding),
                                )
                            else ->
                                MessageRow(
                                    message = message,
                                    isStreamingPlaceholder = isStreamingPlaceholder,
                                    canRetry = message.role == "assistant" && !sending && !isStreamingPlaceholder,
                                    canEdit = message.role == "user" && !sending,
                                    canDelete = message.role != "system" && message.id !in deletingMessageIds,
                                    isDeleting = message.id in deletingMessageIds,
                                    onCopied = { status = "Copied ${message.role} message" },
                                    onRetry = {
                                        val previousUserMessage = history.take(index).lastOrNull { it.role == "user" }
                                        if (previousUserMessage == null) {
                                            status = "No previous user message to retry"
                                        } else {
                                            status = "Retrying previous user message..."
                                            sendContent(previousUserMessage.content)
                                        }
                                    },
                                    onEdit = { editingId = message.id },
                                    onDelete = onDelete,
                                    modifier = Modifier.padding(top = topPadding),
                                )
                        }
                    }
                }
            }
            if (!isAtBottom) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isAtBottom,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it / 2 }),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.End,
                    ) {
                        ScrollToBottomButton(
                            onClick = { scope.launch { listState.animateScrollToItem(history.lastIndex.coerceAtLeast(0)) } },
                            modifier = Modifier.padding(end = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Message") },
            minLines = 2,
            trailingIcon = {
                ActionIconButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    description = "Send message",
                    onClick = sendCurrentInput,
                    enabled = !sending && input.isNotBlank(),
                    modifier = Modifier.size(32.dp),
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                            sendCurrentInput()
                            true
                        } else {
                            false
                        }
                    },
        )
        StreamingStatusLine(visible = sending)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(
                icon = Icons.Filled.History,
                description = "Load older history",
                onClick = { history = agentManager.loadOlderHistory() },
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Clear conversation",
                onClick = {
                    modalRequester.requestConfirmation(
                        ComposeConfirmationModal(
                            title = "Clear conversation?",
                            message = "This removes the persisted conversation history for the current session.",
                            confirmDescription = "Clear conversation",
                        ) {
                            agentManager.clearHistory()
                            history = agentManager.getHistory()
                            status = "Conversation cleared"
                        },
                    )
                },
            )
        }
        PanelStatus(status)
    }
    if (editingId != null) {
        val message = history.find { it.id == editingId }
        if (message != null) {
            EditMessageModal(
                content = message.content,
                onDismiss = { editingId = null },
                onSave = { newContent ->
                    editingId?.let { id ->
                        agentManager.updateMessageContentById(id, newContent)
                        history = agentManager.getHistory()
                    }
                    editingId = null
                },
            )
        }
    }
}

@Composable
internal fun ConversationStartupScrollEffect(
    history: List<Message>,
    listState: LazyListState,
) {
    LaunchedEffect(Unit) {
        if (history.isNotEmpty()) {
            listState.scrollToItem(history.lastIndex)
        }
    }
}

@Composable
private fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionIconButton(
        icon = Icons.Filled.ExpandCircleDown,
        description = "Scroll to latest message",
        onClick = onClick,
        modifier =
            modifier
                .size(44.dp)
                .background(Color(0xFF23252F), shape = MaterialTheme.shapes.small)
                .padding(8.dp),
        iconSize = 26.dp,
    )
}

@Composable
private fun StreamingStatusLine(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(180)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Thinking",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PulsingDots()
        }
    }
}

@Composable
private fun PulsingDots() {
    val transition = rememberInfiniteTransition(label = "streaming")
    val offsets = listOf(0, 160, 320)
    val alphas =
        offsets.map { delayMs ->
            val animatedAlpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = STREAMING_DOT_ANIMATION_CYCLE_MS),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                        initialStartOffset =
                            androidx.compose.animation.core
                                .StartOffset(offsetMillis = delayMs),
                    ),
                label = "streaming-dot-$delayMs",
            )
            animatedAlpha
        }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        alphas.forEach { alpha ->
            Box(
                modifier =
                    Modifier
                        .size(4.dp)
                        .alpha(alpha)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
            )
        }
    }
}

@Composable
private fun MessageRow(
    message: Message,
    isStreamingPlaceholder: Boolean,
    canRetry: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    isDeleting: Boolean,
    onCopied: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == "user"
    val backgroundColor = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    AnimatedVisibility(
        visible = !isDeleting,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(DELETE_ANIMATION_DURATION_MS)),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        PanelContentCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = backgroundColor,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.role.uppercase(),
                    color = if (isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ActionIconButton(
                    icon = Icons.Filled.ContentCopy,
                    description = "Copy ${message.role} message",
                    modifier = Modifier.size(28.dp),
                    onClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        onCopied()
                    },
                )
                if (canEdit) {
                    ActionIconButton(
                        icon = Icons.Filled.Edit,
                        description = "Edit ${message.role} message",
                        modifier = Modifier.size(28.dp),
                        onClick = onEdit,
                    )
                }
                if (canDelete) {
                    ActionIconButton(
                        icon = Icons.Filled.Delete,
                        description = "Delete ${message.role} message",
                        modifier = Modifier.size(28.dp),
                        onClick = onDelete,
                    )
                }
                if (canRetry) {
                    ActionIconButton(
                        icon = Icons.Filled.Refresh,
                        description = "Retry from previous user message",
                        modifier = Modifier.size(28.dp),
                        onClick = onRetry,
                    )
                }
            }
            if (isStreamingPlaceholder) {
                Text(
                    text = "Thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                ComposeMarkdown(message.content)
            }
        }
    }
}

@Composable
private fun EditMessageModal(
    content: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var edited by remember { mutableStateOf(content) }
    ComposeContentModal(title = "Edit message") { dismiss ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = edited,
                onValueChange = { edited = it },
                label = { Text("Content") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                ActionIconButton(icon = Icons.Filled.Close, description = "Cancel edit", onClick = dismiss)
                ActionIconButton(
                    icon = Icons.Filled.Done,
                    description = "Save message",
                    enabled = edited.isNotBlank(),
                    onClick = { onSave(edited) },
                )
            }
        }
    }
}

@Composable
private fun ToolMessageRow(
    message: Message,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = remember(message.metadata) { parseToolMetadata(message.metadata) }
    var expanded by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = !isDeleting,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(DELETE_ANIMATION_DURATION_MS)),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        PanelContentCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TOOL · ${metadata.toolId}",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = metadata.durationMillis?.let { "${it}ms" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    description = "Delete tool call",
                    modifier = Modifier.size(28.dp),
                    onClick = onDelete,
                )
                ActionIconButton(
                    icon = Icons.Filled.ExpandCircleDown,
                    description = if (expanded) "Collapse tool details" else "Expand tool details",
                    modifier = Modifier.size(28.dp),
                    onClick = { expanded = !expanded },
                )
            }
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (metadata.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                    metadata.inputJson?.takeIf { it.isNotBlank() }?.let {
                        DetailBlock(label = "Input", content = it)
                    }
                    metadata.resultContent?.takeIf { it.isNotBlank() }?.let {
                        DetailBlock(label = "Result", content = it)
                    }
                    metadata.resultError?.takeIf { it.isNotBlank() }?.let {
                        DetailBlock(label = "Error", content = it)
                    }
                }
            }
        }
    }
}

private data class ParsedToolMetadata(
    val toolId: String,
    val status: String,
    val durationMillis: Long?,
    val inputJson: String?,
    val resultContent: String?,
    val resultError: String?,
)

private fun parseToolMetadata(metadata: String?): ParsedToolMetadata {
    val json =
        metadata
            ?.let {
                runCatching {
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(it)
                }.getOrNull()
            }?.jsonObject
    return ParsedToolMetadata(
        toolId = json?.get("toolId")?.jsonPrimitive?.content ?: "tool",
        status = json?.get("status")?.jsonPrimitive?.content ?: "ok",
        durationMillis = json?.get("durationMillis")?.jsonPrimitive?.longOrNull,
        inputJson = json?.get("inputJson")?.jsonPrimitive?.content,
        resultContent = json?.get("resultContent")?.jsonPrimitive?.content,
        resultError = json?.get("resultError")?.jsonPrimitive?.content,
    )
}

@Composable
private fun SubAgentMessageRow(
    message: Message,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = remember(message.metadata) { parseSubAgentMetadata(message.metadata) }
    var expanded by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = !isDeleting,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(DELETE_ANIMATION_DURATION_MS)),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        PanelContentCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AGENT · ${metadata.agentName ?: "sub-agent"}",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (metadata.success) "completed" else "failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (metadata.success) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    description = "Delete sub-agent message",
                    modifier = Modifier.size(28.dp),
                    onClick = onDelete,
                )
                ActionIconButton(
                    icon = Icons.Filled.ExpandCircleDown,
                    description = if (expanded) "Collapse sub-agent details" else "Expand sub-agent details",
                    modifier = Modifier.size(28.dp),
                    onClick = { expanded = !expanded },
                )
            }
            if (expanded) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private data class ParsedSubAgentMetadata(
    val jobId: String,
    val success: Boolean,
    val agentId: String?,
    val agentName: String?,
)

private fun parseSubAgentMetadata(metadata: String?): ParsedSubAgentMetadata {
    val json =
        metadata
            ?.let {
                runCatching {
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(it)
                }.getOrNull()
            }?.jsonObject
    return ParsedSubAgentMetadata(
        jobId = json?.get("jobId")?.jsonPrimitive?.content ?: "",
        success = json?.get("success")?.jsonPrimitive?.booleanOrNull ?: false,
        agentId = json?.get("agentId")?.jsonPrimitive?.content,
        agentName = json?.get("agentName")?.jsonPrimitive?.content,
    )
}

@Composable
private fun DetailBlock(
    label: String,
    content: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Todo panel for creating, editing, and managing persisted todos.
 *
 * Use cases: UC-0000013, UC-0000071.
 *
 * @param agentManager Source of todo persistence and updates
 * @param modalRequester Modal requester used for destructive confirmations
 */
@Composable
internal fun TodoPanel(
    agentManager: AgentManager,
    modalRequester: ComposeModalRequester,
) {
    var todos by remember { mutableStateOf(agentManager.getTodosFromDb()) }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TodoPriority.MEDIUM) }
    var statusFilter by remember { mutableStateOf(ALL_TODO_STATUSES) }
    val refresh = {
        todos = agentManager.getTodosFromDb()
    }
    val visibleTodos =
        todos.filter { todo ->
            statusFilter == ALL_TODO_STATUSES || todo.status.name == statusFilter
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("New todo") },
                trailingIcon = {
                    ActionIconButton(
                        icon = Icons.Filled.Add,
                        description = "Add todo",
                        onClick = {
                            val text = description.trim()
                            if (text.isNotBlank()) {
                                agentManager.todoManager.add(text, priority)
                                description = ""
                                refresh()
                            }
                        },
                        enabled = description.isNotBlank(),
                        modifier = Modifier.size(32.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            PanelDropdownField(
                label = "Priority",
                selectedValue = priority.name,
                options = TodoPriority.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = { priority = TodoPriority.valueOf(it) },
                modifier = Modifier.weight(0.45f),
            )
        }
        PanelDropdownField(
            label = "Status filter",
            selectedValue = statusFilter,
            options =
                listOf(PanelSelectOption(ALL_TODO_STATUSES, "All statuses")) +
                    TodoStatus.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
            onSelected = { statusFilter = it },
        )
        Text(
            text = "Total ${todos.size} · showing ${visibleTodos.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (visibleTodos.isEmpty()) {
                PanelEmptyState(title = "No todos", body = "Add a task or change the status filter.")
            } else {
                visibleTodos.forEach { todo ->
                    TodoRow(todo, agentManager, modalRequester, refresh)
                }
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    agentManager: AgentManager,
    modalRequester: ComposeModalRequester,
    refresh: () -> Unit,
) {
    PanelContentCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
    ) {
        Text(todo.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${todo.priority.name.labelizeEnumName()} · ${todo.status.name.labelizeEnumName()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelDropdownField(
                label = "Status",
                selectedValue = todo.status.name,
                options = TodoStatus.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = {
                    agentManager.todoManager.updateStatus(todo.id, TodoStatus.valueOf(it))
                    refresh()
                },
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = Icons.Filled.Edit,
                description = "Edit todo",
                onClick = {
                    modalRequester.request(
                        ComposeContentModal(title = "Edit todo") { dismiss ->
                            TodoEditor(
                                todo = todo,
                                onCancel = dismiss,
                                onSave = { updatedDescription, updatedPriority, updatedStatus ->
                                    agentManager.todoManager.update(todo.id, updatedDescription, updatedPriority)
                                    agentManager.todoManager.updateStatus(todo.id, updatedStatus)
                                    refresh()
                                    dismiss()
                                },
                            )
                        },
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.PlayArrow,
                description = "Start todo",
                enabled = todo.status != TodoStatus.IN_PROGRESS,
                onClick = {
                    agentManager.todoManager.updateStatus(todo.id, TodoStatus.IN_PROGRESS)
                    refresh()
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Done,
                description = "Complete todo",
                enabled = todo.status != TodoStatus.COMPLETED,
                onClick = {
                    agentManager.todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)
                    refresh()
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Delete todo",
                onClick = {
                    modalRequester.requestConfirmation(
                        ComposeConfirmationModal(
                            title = "Delete todo?",
                            message = "Delete '${todo.description}' from the persisted todo list.",
                            confirmDescription = "Delete todo",
                        ) {
                            agentManager.todoManager.remove(todo.id)
                            refresh()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun TodoEditor(
    todo: Todo,
    onCancel: () -> Unit,
    onSave: (String, TodoPriority, TodoStatus) -> Unit,
) {
    var description by remember(todo.id) { mutableStateOf(todo.description) }
    var priority by remember(todo.id) { mutableStateOf(todo.priority) }
    var status by remember(todo.id) { mutableStateOf(todo.status) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelDropdownField(
                label = "Priority",
                selectedValue = priority.name,
                options = TodoPriority.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = { priority = TodoPriority.valueOf(it) },
                modifier = Modifier.weight(1f),
            )
            PanelDropdownField(
                label = "Status",
                selectedValue = status.name,
                options = TodoStatus.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = { status = TodoStatus.valueOf(it) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            ActionIconButton(icon = Icons.Filled.Close, description = "Cancel edit", onClick = onCancel)
            ActionIconButton(
                icon = Icons.Filled.Done,
                description = "Save todo",
                enabled = description.isNotBlank(),
                onClick = { onSave(description.trim(), priority, status) },
            )
        }
    }
}

private const val ALL_TODO_STATUSES = "__all__"
private const val DELETE_ANIMATION_DURATION_MS = 220
private const val STREAMING_DOT_ANIMATION_CYCLE_MS = 700
