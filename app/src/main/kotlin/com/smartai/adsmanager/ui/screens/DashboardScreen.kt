package com.smartai.adsmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartai.adsmanager.domain.CaptureStatus
import com.smartai.adsmanager.ui.MainViewModel
import com.smartai.adsmanager.ui.Routes
import com.smartai.adsmanager.ui.components.ErrorBanner
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
    val delta = (nowMs - epochMs).coerceAtLeast(0L)
    return formatElapsed(delta) + " ago"
}

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    // Ticking clock so "last frame … ago" stays fresh and exposes a frozen capture.
    var nowMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val status = ui.assistanceStatus
    val captureActive = status.captureStatus == CaptureStatus.ACTIVE
    val agentRunning = status.agentRunning

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Smart AI Ads Manager",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                viewModel.safetySummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Start / Stop controls
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.isMonitoring) {
                Button(
                    onClick = { viewModel.stopAssistance() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text("Stop Assistance", modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                ElevatedButton(
                    onClick = { viewModel.requestAssistance() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null)
                    Text("Start Ad Assistance", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        ui.errorMessage?.let { message ->
            ErrorBanner(message, onDismiss = { viewModel.clearError() })
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Screen Capture Status
            item {
                SectionCard("Screen Capture Status") { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        StateBadge(status.state)
                        StatusRow(
                            "Screen Capture",
                            if (captureActive) "ACTIVE" else "INACTIVE",
                        )
                        StatusRow(
                            "Capture pipeline",
                            status.captureStatus.name,
                        )
                        StatusRow(
                            "AI Agent",
                            if (agentRunning) "RUNNING" else "STOPPED",
                        )
                    }
                }
            }

            // AI Agent Status
            item {
                SectionCard("AI Agent Status") { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        StatusRow("Current state", status.state.name)
                        StatusRow("Message", status.message)
                    }
                }
            }

            // Selected Application
            item {
                SectionCard("Selected Application") { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        val app = ui.selectedApp
                        if (app != null) {
                            StatusRow("Application", app.appName)
                            StatusRow("Package", app.packageName)
                            StatusRow("Status", app.status.name)
                        } else {
                            Text(
                                "No application selected. Connect one from the apps list.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Connected Applications
            item {
                SectionCard("Connected Applications") { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${ui.connections.size} connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { onNavigate(Routes.APPS) }) {
                                Icon(Icons.Default.Apps, contentDescription = null)
                                Text("Manage", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        ui.connections.forEach { c ->
                            StatusRow(c.appName, c.status.name)
                        }
                        if (ui.connections.isEmpty()) {
                            Text(
                                "No applications connected yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Ad Assistance Status (recognition)
            item {
                SectionCard("AI Recognition Status") { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        StatusRow("Screen", ui.analysis?.matchedScreen ?: "Not recognized")
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
                    }
                }
            }

            // Loading Monitor
            item {
                SectionCard("Loading Monitor") { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        if (ui.isMonitoring) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 12.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("Monitoring in progress…")
                            }
                        } else {
                            Text("Monitoring is idle.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusRow("Session duration", formatElapsed(ui.sessionDurationMs))
                        StatusRow("Loading elapsed", formatElapsed(status.elapsedMs))
                        StatusRow(
                            "Last frame",
                            formatAgo(status.lastFrameTimeEpochMs, nowMs),
                        )
                        if (status.timeoutWarning) {
                            Text(
                                "Loading is taking longer than expected.",
                                color = Warning,
                            )
                        }
                        status.lastError?.let {
                            Text("Last error: $it", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Reward History
            item {
                SectionCard("Reward Manager") { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${ui.rewards.size} records", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { onNavigate(Routes.REWARDS) }) {
                                Icon(Icons.Default.History, contentDescription = null)
                                Text("View", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        ui.rewards.take(3).forEach { r ->
                            StatusRow(r.appName, "${r.status.name} · ${r.amount}")
                        }
                        if (ui.rewards.isEmpty()) {
                            Text(
                                "No rewards recorded yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
