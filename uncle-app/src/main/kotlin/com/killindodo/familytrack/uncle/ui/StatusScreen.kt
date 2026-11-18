package com.killindodo.familytrack.uncle.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.killindodo.familytrack.uncle.data.PairingEntity
import com.killindodo.familytrack.uncle.data.UncleDb
import com.killindodo.familytrack.uncle.service.UncleSos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private suspend fun computeWarnings(ctx: Context): List<String> {
    val out = mutableListOf<String>()
    try {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val locOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!locOn) out.add("Location is OFF — family cannot see where you are")
    } catch (_: Exception) {
    }
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
        out.add("Messaging permission missing — finish setup again")
    }
    val count = UncleDb.get(ctx).dao().outboxCount().first()
    if (count > 0) out.add("$count message(s) waiting to send — check signal")
    return out
}

/** The ONLY screen the uncle ever sees after setup. Nothing to break. */
@Composable
fun StatusScreen(pairing: PairingEntity) {
    val ctx = LocalContext.current
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var sosAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            warnings = computeWarnings(ctx)
            delay(4_000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "✓",
            color = Color(0xFF10B981),
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Sharing is ON",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            pairing.uncleName,
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFF334155),
        )

        Spacer(Modifier.height(16.dp))
        warnings.forEach { w ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = Color(0xFFFEE2E2),
                ),
                modifier = Modifier.widthIn(max = 360.dp).padding(vertical = 4.dp),
            ) {
                Text(
                    w,
                    color = Color(0xFF7F1D1D),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                UncleSos.trigger(ctx)
                sosAt = System.currentTimeMillis()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFDC2626),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(96.dp),
        ) {
            Text("SOS", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        sosAt?.let {
            Text(
                "SOS sent — family has been alerted",
                color = Color(0xFF7F1D1D),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Family can check where you are whenever needed.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
        )
    }
}
