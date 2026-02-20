package com.evenai.companion.ui.reconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evenai.companion.domain.model.GlassesState
import com.evenai.companion.ui.viewmodel.MainViewModel

/**
 * Shown automatically when the app detects that left and right lens displays
 * are out of sync (GlassesState.OutOfSync) or when connection is lost.
 *
 * Source: Reddit review — "setup screens out of sync on the lenses; restarting
 * setup resolved the issue." We avoid full restart and instead resend the
 * current page payload to re-align both lenses.
 */
@Composable
fun ResyncScreen(
    onDone: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val glassesState by viewModel.glassesState.collectAsState()

    var phase by remember { mutableStateOf(ResyncPhase.IDLE) }

    // Auto-navigate back when glasses become ready
    LaunchedEffect(glassesState) {
        if (phase == ResyncPhase.RESYNCING && glassesState is GlassesState.Ready) {
            phase = ResyncPhase.DONE
            kotlinx.coroutines.delay(1_200)
            onDone()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (phase) {
                ResyncPhase.IDLE -> {
                    Icon(Icons.Filled.SyncProblem, null,
                        Modifier.size(72.dp),
                        MaterialTheme.colorScheme.error)

                    Spacer(Modifier.height(24.dp))
                    Text("Lenses out of sync",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center,
                        color      = MaterialTheme.colorScheme.onBackground)

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "One lens fell behind. A quick resync will sort it out in a moment.",
                        textAlign  = TextAlign.Center,
                        fontSize   = 15.sp,
                        lineHeight = 22.sp,
                        color      = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )

                    Spacer(Modifier.height(32.dp))

                    Button(
                        onClick = {
                            phase = ResyncPhase.RESYNCING
                            viewModel.resync()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        Text("Resync now", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            phase = ResyncPhase.RECONNECTING
                            viewModel.reconnect()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        Text("Reconnect glasses", fontSize = 15.sp)
                    }
                }

                ResyncPhase.RESYNCING -> {
                    CircularProgressIndicator(
                        Modifier.size(60.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("Resyncing your lenses…",
                        fontSize  = 17.sp,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onBackground)
                }

                ResyncPhase.RECONNECTING -> {
                    Icon(Icons.Filled.BluetoothSearching, null,
                        Modifier.size(60.dp),
                        MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    Text("Reconnecting…",
                        fontSize  = 17.sp,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onBackground)

                    // Watch for completion
                    LaunchedEffect(glassesState) {
                        if (glassesState is GlassesState.Ready) {
                            phase = ResyncPhase.DONE
                            kotlinx.coroutines.delay(1_000)
                            onDone()
                        }
                    }
                }

                ResyncPhase.DONE -> {
                    Icon(Icons.Filled.Check, null,
                        Modifier.size(60.dp),
                        MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    Text("All good!",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private enum class ResyncPhase { IDLE, RESYNCING, RECONNECTING, DONE }
