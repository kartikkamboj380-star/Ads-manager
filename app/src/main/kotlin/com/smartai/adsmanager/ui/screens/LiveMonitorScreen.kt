package com.smartai.adsmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartai.adsmanager.domain.AgentEvent
import com.smartai.adsmanager.domain.AgentEventType
import com.smartai.adsmanager.domain.DetectedElement
import com.smartai.adsmanager.service.MonitoringController
import com.smartai.adsmanager.ui.MainViewModel
import com.smartai.adsmanager.ui.components.SectionCard
import com.smartai.adsmanager.ui.components.StateBadge

private val TargetHighlight = Color(0xFFFFC107)
private val ButtonHighlight = Color(0xFF4CAF50)
private val ErrorHighlight = Color(0xFFF44336)

/**
 * Live monitor: shows, in real time, exactly what the agent is seeing and doing.
 *
 * - The live captured frame with detected elements highlighted (the Loading ad control
 *   in amber, other button-like elements in green).
 * - What the agent recognized (text, screen classification, detected elements).
 * - A rolling activity log of the agent's observations and recommendations.
 *
 * The agent only *observes and guides* — it never taps anything. This screen makes that
 * explicit and gives the user full visibility into the agent's perception.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMonitorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val frame = MonitoringController.latestFrameFlow.collectAsState().value
    val analysis = MonitoringController.analysisFlow.collectAsState().value
    val events = MonitoringController.eventsFlow.collectAsState().value
    val status = ui.assistanceStatus

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Monitor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StateBadge(status.state)

            // Live frame with detected-element overlay.
            SectionCard("Live screen — what the agent sees") { inner ->
                Box(modifier = Modifier.padding(inner)) {
                    if (frame != null && frame.width > 0 && frame.height > 0) {
                        LiveFrameWithOverlay(
                            frame = frame,
                            elements = analysis.detectedElements,
                            targetElement = analysis.targetElement,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Waiting for the next captured frame…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Recognition details.
            SectionCard("Recognition") { inner ->
                Column(modifier = Modifier.padding(inner)) {
                    Text("Classification: ${analysis.screenClassification}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Frames processed: ${analysis.framesProcessed}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "OCR: ${if (analysis.ocrSucceeded) "reading" else "not reading yet"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    analysis.pipelineError?.let {
                        Text("Pipeline: $it", color = MaterialTheme.colorScheme.error)
                    }
                    if (analysis.recognizedText.isNotEmpty()) {
                        Text("Recognized text:", style = MaterialTheme.typography.labelLarge)
                        Text(
                            analysis.recognizedText.take(300),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val elements = analysis.detectedElements.filter { it.text.isNotBlank() }
                    if (elements.isNotEmpty()) {
                        Text("Detected elements (${elements.size}):", style = MaterialTheme.typography.labelLarge)
                        elements.take(12).forEach { el ->
                            Text(
                                "• ${el.text}${if (el.isButtonLike) "  [button]" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // Activity log.
            SectionCard("Agent activity") { inner ->
                Box(modifier = Modifier.padding(inner)) {
                    if (events.isEmpty()) {
                        Text(
                            "No activity yet. Start monitoring to see what the agent observes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(events.reversed()) { event ->
                                EventRow(event)
                            }
                        }
                    }
                }
            }

            Text(
                "The agent only observes and guides — it never taps anything. You stay in control.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveFrameWithOverlay(
    frame: android.graphics.Bitmap,
    elements: List<DetectedElement>,
    targetElement: DetectedElement?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxW = constraints.maxWidth
        val density = LocalDensity.current
        val scale = if (frame.width > 0) maxW.toFloat() / frame.width else 1f
        val displayHeight = with(density) { (frame.height * scale).toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(displayHeight)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Live captured screen",
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
            elements.forEach { el ->
                val isTarget = el == targetElement
                val color = when {
                    isTarget -> TargetHighlight
                    el.isButtonLike -> ButtonHighlight
                    else -> Color.Transparent
                }
                if (color != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .offset(x = (el.left * scale).dp, y = (el.top * scale).dp)
                            .size(
                                width = ((el.right - el.left) * scale).dp,
                                height = ((el.bottom - el.top) * scale).dp,
                            )
                            .border(2.dp, color, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: AgentEvent) {
    val color = when (event.type) {
        AgentEventType.ERROR -> ErrorHighlight
        AgentEventType.DETECTION -> TargetHighlight
        AgentEventType.ACTION -> ButtonHighlight
        AgentEventType.GUIDANCE -> MaterialTheme.colorScheme.primary
        AgentEventType.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(event.message, style = MaterialTheme.typography.bodySmall)
            Text(
                event.type.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
