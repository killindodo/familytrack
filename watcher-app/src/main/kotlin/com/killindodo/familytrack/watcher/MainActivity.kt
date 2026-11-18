package com.killindodo.familytrack.watcher

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.killindodo.familytrack.watcher.data.Cleanup
import com.killindodo.familytrack.watcher.data.Notifier
import com.killindodo.familytrack.watcher.data.WatcherDb
import com.killindodo.familytrack.watcher.ui.AddUncleScreen
import com.killindodo.familytrack.watcher.ui.HistoryScreen
import com.killindodo.familytrack.watcher.ui.HomeScreen
import com.killindodo.familytrack.watcher.ui.WelcomeScreen
import com.killindodo.familytrack.watcher.util.Prefs

private enum class Screen { Home, AddUncle, History }

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
            primary = Color(0xFF0F766E),
            onPrimary = Color.White,
            error = Color(0xFFDC2626),
            background = Color(0xFFF8FAFC),
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val db = remember { WatcherDb.get(ctx) }
            val uncles by remember { db.dao().unclesLive() }.collectAsState(initial = emptyList())

            var permsDone by remember { mutableStateOf(Prefs.permsDone(ctx)) }
            var screen by rememberSaveable { mutableStateOf(Screen.Home) }
            var historyUncleId by rememberSaveable { mutableStateOf(-1L) }

            LaunchedEffect(Unit) {
                Notifier.ensureChannels(ctx)
                Cleanup.schedule(ctx)
            }

            when {
                !permsDone -> WelcomeScreen(onDone = { permsDone = true })
                screen == Screen.AddUncle -> AddUncleScreen(onClose = { screen = Screen.Home })
                screen == Screen.History && historyUncleId >= 0 ->
                    HistoryScreen(historyUncleId, onBack = { screen = Screen.Home })
                else -> HomeScreen(
                    uncles = uncles,
                    onAdd = { screen = Screen.AddUncle },
                    onHistory = { id ->
                        historyUncleId = id
                        screen = Screen.History
                    },
                )
            }
        }
    }
}
