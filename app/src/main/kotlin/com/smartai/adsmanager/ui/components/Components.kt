package com.smartai.adsmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.ui.theme.Warning

/** A titled surface card used across screens. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            content(PaddingValues(16.dp))
        }
    }
}

/** Colored pill describing a [LoadingState]. */
@Composable
fun StateBadge(state: LoadingState) {
    val (label, color) = stateLabel(state)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun stateLabel(state: LoadingState): Pair<String, Color> = when (state) {
    LoadingState.IDLE -> "Idle" to MaterialTheme.colorScheme.onSurfaceVariant
    LoadingState.PERMISSION_REQUESTED -> "Requesting Permission" to Warning
    LoadingState.PERMISSION_GRANTED -> "Permission Granted" to MaterialTheme.colorScheme.tertiary
    LoadingState.CAPTURE_INITIALIZING -> "Starting Capture" to MaterialTheme.colorScheme.tertiary
    LoadingState.CAPTURE_ACTIVE -> "Capture Active" to MaterialTheme.colorScheme.primary
    LoadingState.TARGET_APP_DETECTED -> "App Detected" to MaterialTheme.colorScheme.tertiary
    LoadingState.TARGET_SCREEN_DETECTED -> "Screen Detected" to MaterialTheme.colorScheme.tertiary
    LoadingState.SCREEN_DETECTED -> "Screen Detected" to MaterialTheme.colorScheme.tertiary
    LoadingState.TARGET_BUTTON_AVAILABLE -> "Button Available" to MaterialTheme.colorScheme.primary
    LoadingState.USER_ACTION_REQUIRED -> "Action Required" to Warning
    LoadingState.LOADING -> "Loading" to MaterialTheme.colorScheme.primary
    LoadingState.WAITING_FOR_VERIFICATION -> "Verifying" to MaterialTheme.colorScheme.tertiary
    LoadingState.COMPLETED -> "Completed" to Color(0xFF4ADE80)
    LoadingState.FAILED -> "Failed" to MaterialTheme.colorScheme.error
    LoadingState.TIMEOUT -> "Timeout" to Warning
    LoadingState.CAPTURE_STOPPED -> "Capture Stopped" to MaterialTheme.colorScheme.onSurfaceVariant
    LoadingState.PERMISSION_REVOKED -> "Permission Revoked" to MaterialTheme.colorScheme.error
    LoadingState.STOPPED -> "Stopped" to MaterialTheme.colorScheme.onSurfaceVariant
}

/** Simple status row with an icon and text. */
@Composable
fun StatusRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatusIcon(
    state: LoadingState,
    modifier: Modifier = Modifier,
) {
    val (_, color) = stateLabel(state)
    val icon = when (state) {
        LoadingState.COMPLETED -> Icons.Default.Check
        LoadingState.FAILED, LoadingState.TIMEOUT -> Icons.Default.Warning
        else -> Icons.Default.Check
    }
    Icon(
        imageVector = icon,
        contentDescription = state.name,
        tint = color,
        modifier = modifier,
    )
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
