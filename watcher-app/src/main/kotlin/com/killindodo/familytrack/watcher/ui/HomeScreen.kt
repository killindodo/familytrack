package com.killindodo.familytrack.watcher.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.killindodo.familytrack.core.RelativeTime
import com.killindodo.familytrack.watcher.data.GeofEntity
import com.killindodo.familytrack.watcher.data.UncleEntity
import com.killindodo.familytrack.watcher.data.WatcherDb
import com.killindodo.familytrack.watcher.map.MapCircle
import com.killindodo.familytrack.watcher.map.MapMarker
import com.killindodo.familytrack.watcher.map.OsmMap
import com.killindodo.familytrack.watcher.sms.WatcherSmsReceiver
import com.killindodo.familytrack.watcher.util.checkUncle
import com.killindodo.familytrack.watcher.util.navigateTo
import com.killindodo.familytrack.watcher.util.sendHomeCircle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun uncleColor(u: UncleEntity): Color =
    Color(WatcherSmsReceiver.PALETTE[u.colorIndex % WatcherSmsReceiver.PALETTE.size])

@Composable
fun HomeScreen(
    uncles: List<UncleEntity>,
    onAdd: () -> Unit,
    onHistory: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val db = remember { WatcherDb.get(ctx) }
    var selectedId by rememberSaveable { mutableStateOf(-1L) }
    var checkMsg by remember { mutableStateOf<String?>(null) }
    var circleDialog by remember { mutableStateOf(false) }
    val geofs = remember { mutableStateOf<List<GeofEntity>>(emptyList()) }

    val selected = uncles.firstOrNull { it.id == selectedId }
        ?: uncles.firstOrNull()
    LaunchedEffect(selected?.id) {
        selectedId = selected?.id ?: -1L
        geofs.value = selected?.let { db.dao().geofencesFor(it.id) } ?: emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            markers = uncles.filter { it.lastLat != null && it.lastLng != null }
                .map { MapMarker(it.lastLat!!, it.lastLng!!, uncleColor(it).toArgb(), it.name) },
            circles = selected?.let { sel ->
                geofs.value.map { MapCircle(it.lat, it.lng, it.radiusM, uncleColor(sel).toArgb()) }
            } ?: emptyList(),
            focus = selected?.lastLat?.let { lat ->
                selected.lastLng?.let { lng -> Pair(lat, lng) }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top bar
        Surface(color = Color(0xCCFFFFFF), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("FamilyTrack", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Text("${uncles.size} uncle(s)", fontSize = 13.sp, color = Color(0xFF64748B))
            }
        }

        if (uncles.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 340.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "No uncles connected yet",
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Add your first uncle", fontSize = 16.sp)
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 12.dp),
            ) { Text("+ Add uncle", fontSize = 13.sp) }
        }

        // Bottom uncle card
        if (selected != null) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .background(color = uncleColor(selected), shape = CircleShape),
                        )
                        Text(
                            "  ${selected.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                selected.lastLat?.let { lat ->
                                    selected.lastLng?.let { lng -> navigateTo(ctx, lat, lng) }
                                }
                            },
                            enabled = selected.lastLat != null,
                        ) { Text("Navigate", fontSize = 13.sp) }
                    }
                    Text(
                        buildString {
                            append("Last seen: ")
                            append(RelativeTime.format(selected.lastSeenAt ?: selected.pairedAt, System.currentTimeMillis()))
                            append("  ·  Battery: ")
                            append(selected.lastBat?.let { "$it%" } ?: "—")
                            append("  ·  Speed: ")
                            append(selected.lastSpd?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "—")
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                    )
                    if (checkMsg != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(checkMsg!!, fontSize = 13.sp, color = Color(0xFFB45309))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                checkMsg = "Checking…"
                                checkUncle(ctx, selected) { sent ->
                                    checkMsg = if (sent) "Asked ${selected.name} — answer usually arrives within a minute"
                                    else "Couldn't send — check your signal and try again"
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                        ) { Text("Check location", fontSize = 14.sp) }
                        OutlinedButton(
                            onClick = { onHistory(selected.id) },
                            modifier = Modifier.weight(1f),
                        ) { Text("History", fontSize = 14.sp) }
                        OutlinedButton(
                            onClick = { circleDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = selected.lastLat != null,
                        ) { Text("Home circle", fontSize = 14.sp) }
                    }
                }
            }
        }

        if (circleDialog && selected != null) {
            CircleDialog(
                uncle = selected,
                onDismiss = { circleDialog = false },
                onConfirm = { radius ->
                    circleDialog = false
                    val lat = selected.lastLat
                    val lng = selected.lastLng
                    if (lat != null && lng != null) {
                        sendHomeCircle(ctx, selected, lat, lng, radius) { sent ->
                            checkMsg = if (sent) "Home circle sent to ${selected.name}" else "Couldn't send — check signal"
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun CircleDialog(
    uncle: UncleEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var radius by remember { mutableStateOf("100") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set home circle") },
        text = {
            Column {
                Text(
                    "Centered on his latest position. If he goes further than this from home, you get an alert.",
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = radius,
                    onValueChange = { radius = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Radius in meters") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(radius.toDoubleOrNull() ?: 100.0) }) { Text("Set") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

