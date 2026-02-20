package com.evenai.companion.ui.developer

import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evenai.companion.ble.G1Protocol
import com.evenai.companion.domain.model.DiscoveredCharacteristic
import com.evenai.companion.domain.model.GlassesState
import com.evenai.companion.ui.viewmodel.MainViewModel

/**
 * Hidden developer screen — accessible only via 5x tap on the version label.
 * Displays discovered BLE services and characteristics from the connected glasses.
 * Never shown in normal user flow.
 *
 * Source: Requirement — "hidden Developer Mode to discover and map UUIDs if
 * official UUIDs are not known, gated behind a long press on the version label."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val glassesState by viewModel.glassesState.collectAsState()
    val clipboard    = LocalClipboardManager.current

    val knownUuids = listOf(
        DiscoveredCharacteristic(
            serviceUuid = G1Protocol.SERVICE_UUID.toString(),
            charUuid    = G1Protocol.CHAR_TX_UUID.toString(),
            properties  = BluetoothGattCharacteristic.PROPERTY_WRITE,
            propertiesHuman = "WRITE (app → glasses)"
        ),
        DiscoveredCharacteristic(
            serviceUuid = G1Protocol.SERVICE_UUID.toString(),
            charUuid    = G1Protocol.CHAR_RX_UUID.toString(),
            properties  = BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            propertiesHuman = "NOTIFY (glasses → app)"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Developer Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("For debugging only", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Connection status ─────────────────────────────────────────────
            item {
                DevSection("Connection State") {
                    DevRow("Status", glassesState::class.simpleName ?: "Unknown")
                    DevRow("BLE State", glassesState.toString())
                }
            }

            // ── Protocol constants ────────────────────────────────────────────
            item {
                DevSection("Protocol Constants") {
                    DevRow("Service UUID",  G1Protocol.SERVICE_UUID.toString())
                    DevRow("TX UUID",       G1Protocol.CHAR_TX_UUID.toString())
                    DevRow("RX UUID",       G1Protocol.CHAR_RX_UUID.toString())
                    DevRow("CCCD UUID",     G1Protocol.CCCD_UUID.toString())
                    DevRow("MTU",           G1Protocol.MTU_SIZE.toString())
                    DevRow("Max payload",   "${G1Protocol.MAX_PAYLOAD} bytes")
                    DevRow("Page width",    "${G1Protocol.PAGE_WIDTH_PX} px")
                    DevRow("Font size",     "${G1Protocol.FONT_SIZE_PT} pt")
                    DevRow("Lines/page",    G1Protocol.LINES_PER_PAGE.toString())
                }
            }

            // ── Command bytes ─────────────────────────────────────────────────
            item {
                DevSection("Command Bytes") {
                    DevRow("CMD_AI_RESULT",  "0x${G1Protocol.CMD_AI_RESULT.toInt().and(0xFF).toString(16).uppercase()}")
                    DevRow("CMD_MIC",        "0x${G1Protocol.CMD_MIC.toInt().and(0xFF).toString(16).uppercase()}")
                    DevRow("CMD_HEARTBEAT",  "0x${G1Protocol.CMD_HEARTBEAT.toInt().and(0xFF).toString(16).uppercase()}")
                    DevRow("CMD_TAP_SINGLE", "0x${G1Protocol.CMD_TAP_SINGLE.toInt().and(0xFF).toString(16).uppercase()} 0x01")
                    DevRow("CMD_TAP_DOUBLE", "0x${G1Protocol.CMD_TAP_DOUBLE.toInt().and(0xFF).toString(16).uppercase()} 0x00")
                    DevRow("ACK_SUCCESS",    "0x${G1Protocol.ACK_SUCCESS.toInt().and(0xFF).toString(16).uppercase()}")
                    DevRow("ACK_FAILURE",    "0x${G1Protocol.ACK_FAILURE.toInt().and(0xFF).toString(16).uppercase()}")
                    DevRow("HANDSHAKE",      G1Protocol.HANDSHAKE.joinToString(" ") {
                        "0x${it.toInt().and(0xFF).toString(16).uppercase()}"
                    })
                }
            }

            // ── Discovered characteristics ────────────────────────────────────
            item {
                DevSection("Discovered Characteristics (hardcoded + Nordic UART)") {
                    knownUuids.forEach { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.propertiesHuman,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(c.charUuid)) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, "Copy", Modifier.size(14.dp))
                                }
                            }
                            Text("Service: ${c.serviceUuid}", fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("Char:    ${c.charUuid}", fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // ── Quick actions ─────────────────────────────────────────────────
            item {
                DevSection("Quick Actions") {
                    Button(
                        onClick = { viewModel.resync() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Force resync both arms") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.reconnect() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Disconnect and rescan") }
                }
            }
        }
    }
}

@Composable
private fun DevSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun DevRow(key: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, fontSize = 11.sp,
            color  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(120.dp))
        Text(value, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color      = MaterialTheme.colorScheme.onSurface)
    }
}
