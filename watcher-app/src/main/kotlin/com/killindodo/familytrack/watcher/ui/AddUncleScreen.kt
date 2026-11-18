package com.killindodo.familytrack.watcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.killindodo.familytrack.watcher.data.PairCodeEntity
import com.killindodo.familytrack.watcher.data.WatcherDb
import com.killindodo.familytrack.watcher.sms.WatcherSmsReceiver
import com.killindodo.familytrack.watcher.util.Prefs
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Shows the pairing QR + 6-digit code. The uncle's phone scans this. */
@Composable
fun AddUncleScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { WatcherDb.get(ctx) }
    val uncles by remember { db.dao().unclesLive() }.collectAsState(initial = emptyList())

    var watcherNumber by rememberSaveable { mutableStateOf(Prefs.watcherNumber(ctx)) }
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (code.isBlank()) {
            code = (100000..999999).random().toString()
            db.dao().putPairCode(PairCodeEntity(code = code, createdAt = System.currentTimeMillis()))
        }
    }

    val qrJson = JSONObject()
        .put("v", 1)
        .put("c", code)
        .put("n", watcherNumber.trim())
        .toString()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Add an uncle",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Done") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "On his phone: open FamilyTrack → tap “Get started” → enter his name → tap “Scan code” and point the camera at this square.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF334155),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        if (watcherNumber.trim().length < 8) {
            OutlinedTextField(
                value = watcherNumber,
                onValueChange = { watcherNumber = it },
                label = { Text("Your phone number (with country code, e.g. +91…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Type your number to generate the QR code — his phone will send replies to it.",
                fontSize = 13.sp,
                color = Color(0xFFB45309),
                textAlign = TextAlign.Center,
            )
        } else {
            val qrBitmap = remember(qrJson) { generateQr(qrJson) }
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Pairing QR code",
                modifier = Modifier.size(250.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Or type this code on his phone:", style = MaterialTheme.typography.bodyMedium)
        Text(
            code.ifBlank { "······" },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
        )
        Text("Code is valid until used. One uncle per code.", fontSize = 12.sp, color = Color(0xFF64748B))

        Spacer(Modifier.height(24.dp))
        Text(
            "Connected (${uncles.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        uncles.forEach { u ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).padding(vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                color = Color(WatcherSmsReceiver.PALETTE[u.colorIndex % WatcherSmsReceiver.PALETTE.size]),
                                shape = CircleShape,
                            ),
                    )
                    Text("  ${u.name}  ·  ${u.number}", fontSize = 15.sp)
                }
            }
        }
        if (uncles.isEmpty()) {
            Text("No one connected yet — waiting for him to scan…", fontSize = 13.sp, color = Color(0xFF64748B))
        }
    }

    LaunchedEffect(watcherNumber) { Prefs.setWatcherNumber(ctx, watcherNumber.trim()) }
}

private fun generateQr(content: String): Bitmap {
    val size = 480
    val bits = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (bits.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return bmp
}
