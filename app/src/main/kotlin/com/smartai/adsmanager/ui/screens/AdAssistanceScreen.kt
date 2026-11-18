package com.smartai.adsmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartai.adsmanager.domain.CaptureStatus
import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.domain.TargetScreenOption
import com.smartai.adsmanager.ui.MainViewModel
import com.smartai.adsmanager.ui.components.SectionCard
import com.smartai.adsmanager.ui.components.StateBadge
import com.smartai.adsmanager.ui.components.StatusRow
import com.smartai.adsmanager.ui.theme.Warning
import kotlinx.coroutines.delay

private fun formatElapsed(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> "${ms / 1000}s"
    else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
}

private fun formatAgo(epochMs: Long, nowMs: Long): String {
    if (epochMs <= 0L) return "—"
    return formatElapsed((nowMs - epochMs).coerceAtLeast(0L)) + " ago"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdAssistanceScreen(viewModel: MainViewModel) {
    val ui by viewModel.ui.collectAsState()
    var amountText by remember { mutableStateOf("1.0") }
    var nowMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val state = ui.assistanceStatus.state
    val status = ui.assistanceStatus
    val guidance = viewModel.aiCore.guidance(state)
    val captureActive = status.captureStatus == CaptureStatus.ACTIVE

    Scaffold(topBar = { TopAppBar(title = { Text("Ad Assistance") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StateBadge(state)

            // Capture / permission status
            SectionCard("Capture & Permission") { inner ->
                Column(modifier = Modifier.padding(inner)) {
                    StatusRow("Screen Capture", if (captureActive) "ACTIVE" else "INACTIVE")
                    StatusRow("Capture pipeline", status.captureStatus.name)
                    StatusRow("AI Agent", if (status.agentRunning) "RUNNING" else "STOPPED")
                    StatusRow("Last frame", formatAgo(status.lastFrameTimeEpochMs, nowMs))
                    status.lastError?.let {
                        Text("Error: $it", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // AI guidance
            SectionCard("AI Agent Guidance") { inner ->
                Column(modifier = Modifier.padding(inner)) {
                    Text(guidance, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Screen analysis
            SectionCard("Screen Recognition") { inner ->
                Column(modifier = Modifier.padding(inner)) {
                    StatusRow("Mining screen", ui.analysis?.matchedScreen ?: "Not recognized")
                    StatusRow(
                        "Loading ad control",
                        if (ui.analysis?.targetButtonAvailable == true) "Available" else "Not available",
                    )
                    StatusRow(
                        "Loading indicator",
                        if (status.loadingIndicatorVisible) "Visible" else "Not visible",
                    )
                    val confidence = ui.analysis?.confidence ?: 0.0
                    StatusRow("Confidence", "${(confidence * 100).toInt()}%")

                    val detected = ui.analysis?.detectedOptions ?: emptySet()
                    if (detected.isNotEmpty()) {
                        Text(
                            "Options detected on screen:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            detected.forEach { option ->
                                val isTarget = option == TargetScreenOption.LOADING_AD
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isTarget) MaterialTheme.colorScheme.primary else
                                                MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(999.dp),
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        option.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isTarget) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isTarget) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        if (detected.contains(TargetScreenOption.LOADING_AD)) {
                            Text(
                                "Only the Loading ad control is a permitted target. The assistant " +
                                    "never interacts with other options automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            // Loading monitor
            SectionCard("Loading Monitor") { inner ->
                Column(modifier = Modifier.padding(inner)) {
                    StatusRow("State", state.name)
                    StatusRow("Session duration", formatElapsed(ui.sessionDurationMs))
                    StatusRow("Loading elapsed", "${status.elapsedMs / 1000}s")
                    if (status.timeoutWarning) {
                        Text(
                            "Timeout warning: loading is taking longer than expected.",
                            color = Warning,
                        )
                    }
                }
            }

            // Controls
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    state == LoadingState.USER_ACTION_REQUIRED ||
                        state == LoadingState.TARGET_BUTTON_AVAILABLE -> {
                        Button(
                            onClick = { viewModel.userStartedLoading() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null)
                            Text("I tapped Loading ad", modifier = Modifier.padding(start = 8.dp))
                        }
                    }

                    state == LoadingState.TIMEOUT || state == LoadingState.FAILED -> {
                        Button(
                            onClick = { viewModel.requestRetry() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Text("Retry", modifier = Modifier.padding(start = 8.dp))
                        }
                    }

                    else -> {
                        OutlinedButton(
                            onClick = { viewModel.requestAssistance() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null)
                            Text("Start Monitoring", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                if (ui.isMonitoring) {
                    Button(
                        onClick = { viewModel.stopAssistance() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Text("Stop", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Manual reward confirmation (used when no official integration exists).
            SectionCard("Manual Reward Confirmation") { inner ->
                Column(modifier = Modifier.padding(inner)) {
                    Text(
                        "If no official reward confirmation is available, confirm manually. " +
                            "Such records are labeled Unverified and are never claimed as verified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = {
                            amountText.toDoubleOrNull()?.let { viewModel.manualConfirmReward(it) }
                        }) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}
