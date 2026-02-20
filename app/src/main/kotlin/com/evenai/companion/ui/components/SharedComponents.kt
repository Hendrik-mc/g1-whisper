package com.evenai.companion.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evenai.companion.domain.model.GlassesState
import com.evenai.companion.domain.model.LensPage

// ── Connection Status Bar ─────────────────────────────────────────────────────
@Composable
fun ConnectionStatusBar(
    state: GlassesState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (label, color, icon) = when (state) {
        GlassesState.Ready       -> Triple("G1 connected", MaterialTheme.colorScheme.primary, Icons.Filled.Check)
        GlassesState.Connected   -> Triple("Connecting…",  MaterialTheme.colorScheme.secondary, Icons.Filled.BluetoothSearching)
        GlassesState.Connecting  -> Triple("Connecting…",  MaterialTheme.colorScheme.secondary, Icons.Filled.BluetoothSearching)
        GlassesState.Scanning    -> Triple("Searching…",   MaterialTheme.colorScheme.secondary, Icons.Filled.BluetoothSearching)
        GlassesState.Disconnected -> Triple("Glasses not connected", Color(0xFF888888), Icons.Filled.BluetoothDisabled)
        GlassesState.OutOfSync   -> Triple("Display out of sync", MaterialTheme.colorScheme.error, Icons.Filled.Warning)
        is GlassesState.Error    -> Triple("Connection error", MaterialTheme.colorScheme.error, Icons.Filled.Warning)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), color)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))

        val showReconnect = state is GlassesState.Disconnected || state is GlassesState.Error
        AnimatedVisibility(showReconnect) {
            TextButton(onClick = onReconnect) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reconnect", fontSize = 12.sp)
            }
        }
    }
}

// ── Lens Preview ──────────────────────────────────────────────────────────────
// Shows what is currently displayed on the G1 lens — consumer-friendly label.
@Composable
fun LensPreview(
    page: LensPage?,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111111))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(28.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else if (page == null) {
            Text(
                "Nothing on the lens yet.\nSwitch to a widget to get started.",
                modifier  = Modifier.align(Alignment.Center),
                color     = Color(0xFF555555),
                fontSize  = 13.sp,
                lineHeight = 20.sp
            )
        } else {
            Column {
                page.lines.forEach { line ->
                    Text(
                        text       = line,
                        color      = Color(0xFF00E676),
                        fontSize   = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Page ${page.pageIndex + 1} of ${page.totalPages}",
                    color    = Color(0xFF444444),
                    fontSize = 10.sp
                )
            }
        }

        // Lens label
        Text(
            "LENS",
            modifier  = Modifier.align(Alignment.TopEnd),
            fontSize  = 9.sp,
            color     = Color(0xFF333333),
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

// ── Mic Button ────────────────────────────────────────────────────────────────
@Composable
fun MicButton(
    isActive: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "mic_scale"
    )

    FilledIconButton(
        onClick  = onToggle,
        enabled  = enabled,
        modifier = modifier
            .size(64.dp)
            .then(if (isActive) Modifier.scale(scale) else Modifier),
        colors   = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Icon(
            imageVector = if (isActive) Icons.Filled.Mic else Icons.Filled.MicOff,
            contentDescription = if (isActive) "Stop microphone" else "Start microphone",
            modifier = Modifier.size(28.dp),
            tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
