package com.killindodo.familytrack.watcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.killindodo.familytrack.watcher.data.FixEntity
import com.killindodo.familytrack.watcher.data.GeofEntity
import com.killindodo.familytrack.watcher.data.UncleEntity
import com.killindodo.familytrack.watcher.data.WatcherDb
import com.killindodo.familytrack.watcher.map.MapCircle
import com.killindodo.familytrack.watcher.map.MapMarker
import com.killindodo.familytrack.watcher.map.MapTrail
import com.killindodo.familytrack.watcher.map.OsmMap
import com.killindodo.familytrack.watcher.sms.WatcherSmsReceiver
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(uncleId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { WatcherDb.get(ctx) }
    val uncle = remember { mutableStateOf<UncleEntity?>(null) }
    val geofs = remember { mutableStateOf<List<GeofEntity>>(emptyList()) }
    var dayOffset by rememberSaveable { mutableStateOf(0) }
    var fixes by remember { mutableStateOf<List<FixEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        uncle.value = db.dao().uncle(uncleId)
        geofs.value = db.dao().geofencesFor(uncleId)
    }

    LaunchedEffect(dayOffset) {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone).minusDays(dayOffset.toLong())
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        fixes = db.dao().fixesBetween(uncleId, from, to)
    }

    val color = uncle.value?.let {
        Color(WatcherSmsReceiver.PALETTE[it.colorIndex % WatcherSmsReceiver.PALETTE.size])
    } ?: Color(0xFF0F766E)
    val colorInt = color.toArgb()

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier
                    .size(14.dp)
                    .background(color = color, shape = CircleShape),
            )
            Text(
                "  ${uncle.value?.name ?: "Uncle"} — history",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }

        // Day chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 0..6) {
                val date = LocalDate.now(ZoneId.systemDefault()).minusDays(i.toLong())
                val label = when (i) {
                    0 -> "Today"
                    1 -> "Yesterday"
                    else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                }
                if (i == dayOffset) {
                    Button(onClick = { dayOffset = i }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))) {
                        Text(label, fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(onClick = { dayOffset = i }) { Text(label, fontSize = 13.sp) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Trail map
        OsmMap(
            markers = fixes.map {
                MapMarker(
                    it.lat, it.lng, colorInt,
                    java.time.Instant.ofEpochMilli(it.receivedAt)
                        .atZone(ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                )
            },
            trails = if (fixes.size >= 2) {
                listOf(MapTrail(fixes.map { it.lat to it.lng }, colorInt))
            } else {
                emptyList()
            },
            circles = geofs.value.map { MapCircle(it.lat, it.lng, it.radiusM, colorInt) },
            focus = fixes.lastOrNull()?.let { Pair(it.lat, it.lng) },
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )

        Spacer(Modifier.height(8.dp))
        Text(
            if (fixes.isEmpty()) "No locations recorded this day."
            else "${fixes.size} location(s):",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(fixes.reversed()) { f ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            java.time.Instant.ofEpochMilli(f.receivedAt)
                                .atZone(ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm:ss")),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Text(
                            "accuracy ${f.acc.roundToInt()} m · battery ${f.bat}% · ${f.source.lowercase()}",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                        )
                    }
                }
            }
        }
    }
}
