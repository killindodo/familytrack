package com.killindodo.familytrack.watcher.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.killindodo.familytrack.watcher.data.Notifier
import com.killindodo.familytrack.watcher.util.Prefs

@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var exactOk by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 31 ||
                (ctx.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()),
        )
    }

    // Re-check the exact-alarm switch when the user returns from system settings.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                exactOk = Build.VERSION.SDK_INT < 31 ||
                    (ctx.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms())
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        smsGranted = res.values.all { it }
        if (smsGranted && Build.VERSION.SDK_INT >= 33 && !notifGranted) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) { Notifier.ensureChannels(ctx) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Welcome to FamilyTrack",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Three quick permissions so your family can reach you and their phones can answer you.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = Color(0xFF334155),
        )
        Spacer(Modifier.height(24.dp))

        PermissionRow("Send + receive SMS", smsGranted) {
            smsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS))
        }
        Spacer(Modifier.height(10.dp))
        PermissionRow("Notifications", notifGranted) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Spacer(Modifier.height(10.dp))
        PermissionRow("Exact alarms (did-not-respond timer)", exactOk) {
            try {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + ctx.packageName),
                    ),
                )
            } catch (_: Exception) {
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                Prefs.markPermsDone(ctx)
                onDone()
            },
            enabled = smsGranted,
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp),
        ) { Text("I'm ready", fontSize = 18.sp) }
        if (!smsGranted) {
            Spacer(Modifier.height(8.dp))
            Text(
                "SMS permission is required — without it the two phones cannot talk.",
                fontSize = 13.sp,
                color = Color(0xFFB45309),
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onAsk: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
        ),
        modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (granted) "✓ " else "○ ",
                fontSize = 18.sp,
                color = if (granted) Color(0xFF166534) else Color(0xFF64748B),
            )
            Text(label, modifier = Modifier.padding(start = 4.dp), fontSize = 15.sp)
            if (!granted) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onAsk) {
                    Text("Allow", fontSize = 14.sp)
                }
            }
        }
    }
}
