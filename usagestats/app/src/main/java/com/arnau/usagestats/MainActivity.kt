package com.arnau.usagestats

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arnau.usagestats.data.UsageAccessChecker
import com.arnau.usagestats.data.UsageOverview
import com.arnau.usagestats.data.UsageStatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(UsageAccessChecker.hasUsageAccess(context)) }

    // Al volver de Ajustes tras conceder el permiso, re-comprobamos en onResume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = UsageAccessChecker.hasUsageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasAccess) {
        StatsScreen()
    } else {
        PermissionScreen()
    }
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Se necesita acceso de uso",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Esta app necesita el permiso especial \"Acceso de uso\" para leer " +
                "cuántas veces desbloqueas el móvil y cuánto tiempo lo usas.\n\n" +
                "Android no permite conceder este permiso con el diálogo habitual: " +
                "hay que activarlo manualmente en Ajustes, buscando esta app en la lista.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }) {
            Text("Abrir ajustes")
        }
    }
}

@Composable
private fun StatsScreen() {
    val context = LocalContext.current
    var overview by remember { mutableStateOf<UsageOverview?>(null) }

    LaunchedEffect(Unit) {
        overview = withContext(Dispatchers.IO) {
            UsageStatsCalculator.calculate(context)
        }
    }

    val data = overview
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (data == null) {
            CircularProgressIndicator()
        } else {
            StatBlock(value = data.unlocksToday.toString(), label = "Desbloqueos hoy")
            Spacer(Modifier.height(32.dp))
            StatBlock(
                value = formatAvg(data.avgUnlocksLast7Days),
                label = "Media desbloqueos (últimos 7 días)"
            )
            Spacer(Modifier.height(32.dp))
            StatBlock(
                value = formatDuration(data.usageMillisToday),
                label = "Tiempo de uso hoy"
            )
            Spacer(Modifier.height(32.dp))
            StatBlock(
                value = formatDuration(data.avgUsageMillisLast7Days.toLong()),
                label = "Media tiempo de uso (últimos 7 días)"
            )
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

private fun formatAvg(avg: Double): String = String.format("%.1f", avg)

private fun formatDuration(millis: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
}
