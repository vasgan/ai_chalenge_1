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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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

@Composable
fun MainRoute(
    vm: ChatViewModel,
    onOpenSettings: () -> Unit,
    onOpenFacts: () -> Unit,
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
        onOpenBranches = onOpenBranches,
        onCreateBranch = onCreateBranch,
        onPauseTask = vm::pauseTask,
        onResumeTask = vm::resumeTask,
        onCancelTask = vm::cancelTask,
        onResetTask = vm::resetTask
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFacts: () -> Unit,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Агент", style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = onOpenBranches) { Text("Ветки") }
                    TextButton(onClick = onOpenFacts) { Text("Показать facts") }
                    TextButton(onClick = onOpenSettings) { Text("Настройки") }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                items(state.messages, key = { it.id }) { msg ->
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
private fun TaskDebugPanel(
    taskState: TaskState?,
    onPauseTask: () -> Unit,
    onResumeTask: () -> Unit,
    onCancelTask: () -> Unit,
    onResetTask: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Task Debug Panel", style = MaterialTheme.typography.titleSmall)
            if (taskState == null) {
                Text("Task state not initialized", style = MaterialTheme.typography.bodySmall)
            } else {
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPauseTask) { Text("Pause") }
                TextButton(onClick = onResumeTask) { Text("Resume") }
                TextButton(onClick = onCancelTask) { Text("Cancel") }
                TextButton(onClick = onResetTask) { Text("Reset task") }
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
                    text = if (isUser) "You" else "Assistant",
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
