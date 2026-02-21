package com.evenai.companion.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evenai.companion.domain.model.GlassesState
import com.evenai.companion.domain.model.LensPage
import com.evenai.companion.domain.model.Widget
import com.evenai.companion.ui.components.ConnectionStatusBar
import com.evenai.companion.ui.components.LensPreview
import com.evenai.companion.ui.components.MicButton
import com.evenai.companion.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

/**
 * Main dashboard: connection status, widget grid (5 widgets), lens preview, mic button.
 * Long-press on version label unlocks Developer Mode (hidden from normal users).
 */
@Composable
fun DashboardScreen(
    onResyncNeeded: () -> Unit,
    onDeveloperMode: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val glassesState    by viewModel.glassesState.collectAsState()
    val activeWidget    by viewModel.activeWidget.collectAsState()
    val widgetSwitching by viewModel.widgetSwitching.collectAsState()
    val micActive       by viewModel.micActive.collectAsState()
    val currentPage     by viewModel.currentPage.collectAsState()
    val devMode         by viewModel.developerMode.collectAsState()

    // Auto-navigate to resync screen when glasses fall out of sync
    LaunchedEffect(glassesState) {
        if (glassesState is GlassesState.OutOfSync) {
            onResyncNeeded()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        // ── Status bar ────────────────────────────────────────────────────────
        ConnectionStatusBar(
            state         = glassesState,
            onReconnect   = { viewModel.reconnect() }
        )

        // ── Lens preview ──────────────────────────────────────────────────────
        LensPreview(
            page    = currentPage,
            loading = widgetSwitching,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ── Widget grid ───────────────────────────────────────────────────────
        Text(
            "Choose a widget",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize   = 13.sp,
            color      = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        LazyVerticalGrid(
            columns       = GridCells.Fixed(2),
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Widget.entries.toTypedArray()) { widget ->
                WidgetCard(
                    widget    = widget,
                    isActive  = widget == activeWidget,
                    isLoading = widgetSwitching && widget == activeWidget,
                    onClick   = { viewModel.switchWidget(widget) }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Mic button ────────────────────────────────────────────────────────
        MicButton(
            isActive  = micActive,
            enabled   = glassesState is GlassesState.Ready,
            onToggle  = { if (micActive) viewModel.stopMic() else viewModel.startMic() },
            modifier  = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )

        // ── Version label (hidden dev mode trigger) ───────────────────────────
        VersionLabel(
            onLongPress = {
                viewModel.toggleDeveloperMode()
                if (devMode) onDeveloperMode()
            }
        )
    }
}

// ── Widget card ───────────────────────────────────────────────────────────────
@Composable
private fun WidgetCard(
    widget: Widget,
    isActive: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "widget_bg"
    )
    val borderColor = if (isActive)
        MaterialTheme.colorScheme.primary
    else
        Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = widgetIcon(widget),
                contentDescription = null,
                modifier    = Modifier.size(28.dp),
                tint        = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                widget.displayName,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontSize   = 14.sp,
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 2
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopEnd),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (isActive && !isLoading) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.TopEnd)
            )
        }
    }
}

// ── Version label with long-press ────────────────────────────────────────────
@Composable
private fun VersionLabel(onLongPress: () -> Unit) {
    var pressCount by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    // Reset press count after 3 seconds of inactivity
    LaunchedEffect(pressCount) {
        if (pressCount > 0) {
            delay(3_000)
            pressCount = 0
        }
    }

    Text(
        text = "v${com.evenai.companion.BuildConfig.VERSION_NAME}",
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable {
                pressCount++
                if (pressCount >= 5) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    pressCount = 0
                    onLongPress()
                }
            },
        textAlign = TextAlign.Center,
        fontSize  = 11.sp,
        color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
    )
}

private fun widgetIcon(widget: Widget): ImageVector = when (widget) {
    Widget.AI_ASSISTANT -> Icons.Filled.SmartToy
    Widget.LIVE_CAPTION -> Icons.Filled.ClosedCaption
    Widget.TRANSLATOR   -> Icons.Filled.Translate
    Widget.TELEPROMPTER -> Icons.Filled.Article
    Widget.NAVIGATION   -> Icons.Filled.Navigation
}
