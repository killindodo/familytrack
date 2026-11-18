package com.killindodo.familytrack.uncle.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.killindodo.familytrack.core.FtMessage
import com.killindodo.familytrack.core.SmsProtocol
import com.killindodo.familytrack.uncle.data.PairingEntity
import com.killindodo.familytrack.uncle.data.UncleDb
import com.killindodo.familytrack.uncle.service.Sender
import com.killindodo.familytrack.uncle.service.UncleJobs
import com.killindodo.familytrack.uncle.service.UncleService
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class Step { Welcome, Name, Pair, Location, Background, Notifications, Battery, Done }

@Composable
fun Wizard(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { WizardStore.load(ctx) }

    var step by rememberSaveable { mutableIntStateOf(saved?.step ?: Step.Welcome.ordinal) }
    var name by rememberSaveable { mutableStateOf(saved?.name ?: "") }
    var code by rememberSaveable { mutableStateOf(saved?.code ?: "") }
    var number by rememberSaveable { mutableStateOf(saved?.number ?: "") }
    var manual by rememberSaveable { mutableStateOf(false) }
    var smsDenied by rememberSaveable { mutableStateOf(false) }

    fun persist() = WizardStore.put(ctx, name, code, number, step)
    fun advance() {
        step++
        persist()
    }

    fun doPair() {
        scope.launch {
            UncleDb.get(ctx).dao().putPairing(
                PairingEntity(
                    uncleName = name.trim(),
                    watcherNumber = number.trim(),
                    code = code.trim(),
                    pairedAt = System.currentTimeMillis(),
                ),
            )
            UncleService.start(ctx)
            UncleJobs.scheduleAll(ctx)
            Sender.sendWithFallback(
                ctx, number.trim(),
                SmsProtocol.encode(FtMessage.PairMsg(code.trim(), name.trim())),
            )
        }
        step = Step.Location.ordinal
        persist()
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        try {
            val o = JSONObject(contents)
            if (o.optInt("v") == 1) {
                code = o.optString("c")
                number = o.optString("n")
                manual = false
                persist()
            }
        } catch (_: Exception) {
            // not a FamilyTrack QR — ignore, screen stays as is
        }
    }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        smsDenied = !it
        doPair()
    }

    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        advance()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        advance()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        advance()
    }

    fun requestSmsAndPair() {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.SEND_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) doPair() else smsLauncher.launch(Manifest.permission.SEND_SMS)
    }

    fun startScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Point at the square code on the family phone")
            setBeepEnabled(false)
            setOrientationLocked(true)
        }
        scanLauncher.launch(options)
    }

    when (Step.entries[step]) {
        Step.Welcome -> StepScreen(
            title = "Welcome to FamilyTrack",
            body = "This app helps your family stay close to you. " +
                "It only shares your location when they ask — never all the time.",
            allowLabel = "Get started",
            onAllow = ::advance,
        )

        Step.Name -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "What is your name?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { persist(); advance() },
                enabled = name.trim().length >= 2,
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp),
            ) { Text("Continue", fontSize = 18.sp) }
        }

        Step.Pair -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Connect to your family",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ask your family member to open FamilyTrack on their phone and show you the square code. Then tap Scan.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color(0xFF334155),
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = ::startScan, modifier = Modifier.height(56.dp)) {
                    Text("Scan code", fontSize = 18.sp)
                }
                OutlinedButton(onClick = { manual = true }, modifier = Modifier.height(56.dp)) {
                    Text("Type instead", fontSize = 18.sp)
                }
            }

            if (manual) {
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("6-digit code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Family phone number (with country code, e.g. +91…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { requestSmsAndPair() },
                    enabled = code.length == 6 && number.trim().length >= 8,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp),
                ) { Text("Connect", fontSize = 18.sp) }
            }

            if (code.isNotBlank() && number.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Ready to connect to $number",
                    color = Color(0xFF10B981),
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { requestSmsAndPair() },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp),
                ) { Text("Connect now", fontSize = 18.sp) }
            }

            if (smsDenied) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Without messaging permission we cannot reach your family. You can continue, but tell your family to finish this later.",
                    color = Color(0xFFB45309),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Step.Location -> StepScreen(
            title = "See where you are",
            body = "When family asks, this app tells them where you are. " +
                "Please allow Location while using the app.",
            allowLabel = "Allow location",
            onAllow = {
                fineLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
            onSkip = ::advance,
        )

        Step.Background -> StepScreen(
            title = "Allow all the time",
            body = "Your family may ask even when this app is closed. " +
                "On the next screen, please choose “Allow all the time”.",
            allowLabel = "Allow all the time",
            onAllow = {
                val fine = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                if (fine) {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    advance()
                }
            },
            onSkip = ::advance,
        )

        Step.Notifications -> StepScreen(
            title = "Keep this app alive",
            body = "A small note stays on your screen: “FamilyTrack is ready”. " +
                "Please allow notifications.",
            allowLabel = "Allow notifications",
            onAllow = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    advance()
                }
            },
            onSkip = ::advance,
        )

        Step.Battery -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "One last thing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Phones try to save battery by closing apps. Please tell your phone to leave FamilyTrack alone.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color(0xFF334155),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { OemHelp.requestBatteryExemption(ctx) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp),
            ) { Text("Give battery permission", fontSize = 18.sp) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { OemHelp.openBrandSettings(ctx) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp),
            ) { Text("Open brand settings", fontSize = 18.sp) }
            Spacer(Modifier.height(16.dp))
            OemHelp.guidance(ctx)?.let { tip ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                ) {
                    Text(tip, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
            Button(
                onClick = ::advance,
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp),
            ) { Text("I did it", fontSize = 18.sp) }
        }

        Step.Done -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✓", color = Color(0xFF10B981), fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Text(
                "All set, ${name.trim()}!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "You can close this app now — FamilyTrack keeps working by itself.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color(0xFF334155),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    WizardStore.markDone(ctx)
                    onFinished()
                },
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp),
            ) { Text("Open my screen", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun StepScreen(
    title: String,
    body: String,
    allowLabel: String,
    onAllow: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = Color(0xFF334155),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp),
        ) { Text(allowLabel, fontSize = 18.sp) }
        if (onSkip != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(56.dp),
            ) { Text("Skip for now", fontSize = 18.sp) }
        }
    }
}
