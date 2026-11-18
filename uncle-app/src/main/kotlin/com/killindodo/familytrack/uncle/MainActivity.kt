package com.killindodo.familytrack.uncle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.killindodo.familytrack.uncle.data.UncleDb
import com.killindodo.familytrack.uncle.ui.StatusScreen
import com.killindodo.familytrack.uncle.ui.Wizard
import com.killindodo.familytrack.uncle.ui.WizardStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Root() }
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF10B981),
            onPrimary = Color.White,
            error = Color(0xFFDC2626),
            background = Color(0xFFF8FAFC),
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val db = remember { UncleDb.get(ctx) }
            val pairing by remember { db.dao().pairingLive() }.collectAsState(initial = null)

            var wizardDone by remember { mutableStateOf(WizardStore.done(ctx)) }
            var unpairedConfirmed by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                if (db.dao().pairing() == null) unpairedConfirmed = true
            }

            when {
                pairing != null && wizardDone -> StatusScreen(pairing!!)
                pairing != null -> Wizard(onFinished = { wizardDone = true })
                unpairedConfirmed -> Wizard(onFinished = { wizardDone = true })
                else -> Unit // brief blank while the database loads
            }
        }
    }
}
