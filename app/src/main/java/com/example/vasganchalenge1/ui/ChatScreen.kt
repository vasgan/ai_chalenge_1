package com.example.vasganchalenge1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vasganchalenge1.data.Role
import com.example.vasganchalenge1.data.RunMetric
import com.example.vasganchalenge1.data.UiChatMessage
import com.example.vasganchalenge1.data.taskfsm.ExpectedAction
import com.example.vasganchalenge1.data.taskfsm.TaskPhase
import com.example.vasganchalenge1.data.taskfsm.TaskState
import com.example.vasganchalenge1.data.taskfsm.TaskStep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoute(
    vm: ChatViewModel,
    onOpenSettings: () -> Unit,
    onOpenFacts: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenBranches: () -> Unit,
    onCreateBranch: (Long) -> Unit
) {
    val state = vm.state.collectAsState().value

    ChatScreen(
        state = state,
        onInputChange = vm::onInputChange,
        onSendClick = vm::onSendClick,
        onOpenSettings = onOpenSettings,
        onOpenFacts = onOpenFacts,
        onOpenMcp = onOpenMcp,
        onOpenBranches = onOpenBranches,
        onCreateBranch = onCreateBranch,
        onPauseTask = vm::pauseTask,
        onResumeTask = vm::resumeTask,
        onCancelTask = vm::cancelTask,
        onResetTask = vm::resetTask
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFacts: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenBranches: () -> Unit,
    onCreateBranch: (Long) -> Unit,
    onPauseTask: () -> Unit,
    onResumeTask: () -> Unit,
    onCancelTask: () -> Unit,
    onResetTask: () -> Unit
) {
    val listState = rememberLazyListState()

    // автоскролл вниз, когда добавились сообщения
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Агент") },
                actions = {
                    IconButton(onClick = onOpenBranches) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallSplit,
                            contentDescription = "Ветки"
                        )
                    }
                    IconButton(onClick = onOpenFacts) {
                        Icon(
                            imageVector = Icons.Filled.FactCheck,
                            contentDescription = "Facts"
                        )
                    }
                    IconButton(onClick = onOpenMcp) {
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = "MCP Server"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Настройки"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AgentToolingBlock(
                    mode = state.toolWorkMode,
                    activeToolServerLabel = state.activeToolServerLabel,
                    activeToolName = state.activeToolName,
                    activePipelineName = state.activePipelineName,
                    activePipelineSteps = state.activePipelineSteps,
                    recentToolActivities = state.recentToolActivities
                )

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("Сообщение") },
                        maxLines = 6
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSendClick,
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text("Send")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // метрики (последняя строка)
            MetricsHeader(metrics = state.metrics)
            McpDebugHeader(
                status = state.mcpConnectionStatus,
                toolsCount = state.mcpToolsCount,
                serverUrl = state.mcpServerUrl,
                servers = state.mcpServers
            )
            TaskDebugPanel(
                taskState = state.taskStateDebug,
                onPauseTask = onPauseTask,
                onResumeTask = onResumeTask,
                onCancelTask = onCancelTask,
                onResetTask = onResetTask
            )

            Divider()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(state.messages, key = { index, msg -> "${msg.id}_$index" }) { _, msg ->
                    ChatBubble(
                        msg = msg,
                        onCreateBranch = { onCreateBranch(msg.id) }
                    )
                }

                // чтобы низ не прилипал к bottomBar
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }
}

@Composable
private fun McpDebugHeader(
    status: String,
    toolsCount: Int,
    serverUrl: String,
    servers: List<McpServerDebugInfo>
) {
    val connected = servers.count { it.status == "CONNECTED" }
    val connectedText = if (servers.isEmpty()) "" else " • Connected: $connected/${servers.size}"
    Text(
        text = "MCP: $status • Tools: $toolsCount$connectedText" +
                if (serverUrl.isNotBlank()) " • URL: $serverUrl" else "",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun AgentToolingBlock(
    mode: ToolWorkMode,
    activeToolServerLabel: String?,
    activeToolName: String?,
    activePipelineName: String?,
    activePipelineSteps: List<PipelineStepDebugInfo>,
    recentToolActivities: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = when (mode) {
                    ToolWorkMode.IDLE -> "Tools: idle"
                    ToolWorkMode.TOOL_CALL_IN_PROGRESS -> "Tools: tool call in progress"
                    ToolWorkMode.PIPELINE_IN_PROGRESS -> "Tools: pipeline in progress"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            if (mode == ToolWorkMode.TOOL_CALL_IN_PROGRESS && !activeToolName.isNullOrBlank()) {
                Text(
                    text = "Working with: [${activeToolServerLabel ?: "unknown"}] $activeToolName",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (mode == ToolWorkMode.PIPELINE_IN_PROGRESS && !activePipelineName.isNullOrBlank()) {
                Text(
                    text = "Pipeline: $activePipelineName",
                    style = MaterialTheme.typography.bodySmall
                )
                activePipelineSteps.forEach { step ->
                    Text(
                        text = "${step.index}. [${step.serverId}] ${step.toolName} ${step.status}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (recentToolActivities.isNotEmpty()) {
                Text(
                    text = "Recent: ${recentToolActivities.take(3).joinToString(" • ")}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun TaskDebugPanel(
    taskState: TaskState?,
    onPauseTask: () -> Unit,
    onResumeTask: () -> Unit,
    onCancelTask: () -> Unit,
    onResetTask: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Task Debug Panel", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть"
                    )
                }
            }
            if (taskState == null) {
                Text("Task state not initialized", style = MaterialTheme.typography.bodySmall)
            } else {
                if (expanded) {
                    TaskPhaseStepper(
                        phase = taskState.phase,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Step: ${describeStep(taskState.currentStep)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "ExpectedAction: ${describeExpectedAction(taskState.expectedAction)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Status: ${taskState.status}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "updatedAt: ${formatTimestamp(taskState.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onPauseTask) { Text("Pause") }
                    TextButton(onClick = onResumeTask) { Text("Resume") }
                    TextButton(onClick = onCancelTask) { Text("Cancel") }
                    TextButton(onClick = onResetTask) { Text("Reset task") }
                }
            }
        }
    }
}

@Composable
private fun TaskPhaseStepper(
    phase: TaskPhase,
    modifier: Modifier = Modifier
) {
    val phases = listOf(
        TaskPhase.PLANNING,
        TaskPhase.EXECUTION,
        TaskPhase.VALIDATION,
        TaskPhase.DONE
    )
    val currentIndex = phases.indexOf(phase).coerceAtLeast(0)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        phases.forEachIndexed { index, item ->
            val style = when {
                index == currentIndex -> PhaseStepStyle.Current
                index < currentIndex -> PhaseStepStyle.Reached
                else -> PhaseStepStyle.Pending
            }
            PhaseStepChip(
                text = phaseLabel(item),
                style = style,
                modifier = Modifier.weight(1f)
            )
            if (index < phases.lastIndex) {
                Spacer(Modifier.width(6.dp))
                PhaseConnector(
                    active = index < currentIndex,
                    modifier = Modifier.width(12.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun PhaseStepChip(
    text: String,
    style: PhaseStepStyle,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = style.borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .background(
                color = style.backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = style.textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun PhaseConnector(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (active) {
        Color(0xFF3B82F6)
    } else {
        Color(0xFFD1D5DB)
    }
    Box(
        modifier = modifier
            .height(1.dp)
            .background(lineColor)
    )
}

private fun phaseLabel(phase: TaskPhase): String {
    return when (phase) {
        TaskPhase.PLANNING -> "PLANNING"
        TaskPhase.EXECUTION -> "EXECUTION"
        TaskPhase.VALIDATION -> "VALIDATION"
        TaskPhase.DONE -> "COMPLETED"
    }
}

private enum class PhaseStepStyle(
    val backgroundColor: Color,
    val borderColor: Color,
    val textColor: Color
) {
    Pending(
        backgroundColor = Color.White,
        borderColor = Color(0xFFD1D5DB),
        textColor = Color(0xFF6B7280)
    ),
    Reached(
        backgroundColor = Color.White,
        borderColor = Color(0xFF3B82F6),
        textColor = Color(0xFF2563EB)
    ),
    Current(
        backgroundColor = Color(0xFF22C55E),
        borderColor = Color(0xFF16A34A),
        textColor = Color.White
    )
}

@Composable
private fun MetricsHeader(metrics: List<RunMetric>) {
    val last = metrics.firstOrNull()
    if (last == null) {
        Text(
            text = "Метрики: —",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }

    val priceText = last.costUsd?.let { "$" + String.format("%.6f", it) } ?: "—"

    Text(
        text = "Model: ${last.model} • Time: ${last.latencyMs}ms • Tokens: ${last.totalTokens} • Price: $priceText  • Total usage tokens: ${last.totalUsageToken}",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ChatBubble(
    msg: UiChatMessage,
    onCreateBranch: () -> Unit
) {
    val isUser = msg.role == Role.USER
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        val isInvariantViolation = !isUser && msg.violatesInvariants
        val bubbleColor = if (isInvariantViolation) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
        val textColor = if (isInvariantViolation) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = when (msg.role) {
                        Role.USER -> "You"
                        Role.ASSISTANT -> "Assistant"
                        Role.TOOL -> "Tool"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor
                )
                Spacer(Modifier.height(4.dp))
                Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = textColor)
                if (isInvariantViolation) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Violation: ответ нарушает инварианты профиля",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(msg.text))
                        }
                    ) {
                        Text("Copy")
                    }
                    TextButton(onClick = onCreateBranch) {
                        Text("Ветка")
                    }
                }
            }
        }
    }
}

private fun describeStep(step: TaskStep): String {
    return when (step) {
        is TaskStep.CollectRequirements -> "id=${step.id}, missing=${step.missingFields.joinToString()}, collected=${step.collectedFields.keys.joinToString()}"
        is TaskStep.CreatePlan -> "id=${step.id}, requirements=${step.requirements.keys.joinToString()}"
        is TaskStep.ImplementFeature -> "id=${step.id}, featureKey=${step.featureKey}"
        is TaskStep.RunChecks -> "id=${step.id}, target=${step.targetFeatureKey}"
        is TaskStep.Finished -> "id=${step.id}, summary=${step.summary.take(80)}"
    }
}

private fun describeExpectedAction(action: ExpectedAction): String {
    return when (action) {
        is ExpectedAction.UserReply -> "${action.type}, missing=${action.missingFields.joinToString()}"
        is ExpectedAction.ToolCall -> "${action.type}, tool=${action.toolName}, hint=${action.hint.take(80)}"
        is ExpectedAction.Idle -> "${action.type}, message=${action.message.take(80)}"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
