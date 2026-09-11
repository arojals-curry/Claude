package com.arnau.usagestats

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arnau.usagestats.data.AppEntry
import com.arnau.usagestats.data.AppUsageDisplay
import com.arnau.usagestats.data.InstalledAppsProvider
import com.arnau.usagestats.data.UsageAccessChecker
import com.arnau.usagestats.data.UsageOverview
import com.arnau.usagestats.data.UsageStatsCalculator
import com.arnau.usagestats.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    /** Se incrementa cada vez que el sistema reenvía un intent de HOME (usuario pulsa Inicio). */
    private val goHomeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(goHomeSignal = goHomeSignal)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN &&
            intent.categories?.contains(Intent.CATEGORY_HOME) == true
        ) {
            goHomeSignal.intValue++
        }
    }
}

@Composable
private fun AppRoot(goHomeSignal: State<Int>) {
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

    // Pantalla de estadísticas: no forma parte del pager, se abre por encima
    // de él con un botón desde el listado de apps.
    var showStats by remember { mutableStateOf(false) }
    LaunchedEffect(goHomeSignal.value) {
        showStats = false
    }

    if (hasAccess) {
        if (showStats) {
            StatsDetailScreen(onBack = { showStats = false })
        } else {
            LauncherPager(goHomeSignal, onOpenStats = { showStats = true })
        }
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

/** Pager de 2 páginas: 0 = inicio (reloj + stats), 1 = listado de apps. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherPager(goHomeSignal: State<Int>, onOpenStats: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Cada vez que el usuario vuelve a pulsar el botón de Inicio del sistema,
    // el pager debe volver a la página principal.
    LaunchedEffect(goHomeSignal.value) {
        pagerState.scrollToPage(0)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> HomeScreen()
            else -> AppsScreen(onOpenStats = onOpenStats)
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var overview by remember { mutableStateOf<UsageOverview?>(null) }

    LaunchedEffect(Unit) {
        overview = withContext(Dispatchers.IO) {
            UsageStatsCalculator.syncToday(context, db)
            UsageStatsCalculator.calculate(context, db)
        }
    }

    val data = overview
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Clock()
        Spacer(Modifier.height(48.dp))
        if (data == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            StatBlock(value = data.unlocksToday.toString(), label = "Desbloqueos hoy")
            Spacer(Modifier.height(28.dp))
            StatBlock(
                value = formatAvg(data.avgUnlocksLast7Days),
                label = "Media desbloqueos (últimos 7 días)"
            )
            Spacer(Modifier.height(28.dp))
            StatBlock(
                value = formatDuration(data.usageMillisToday),
                label = "Tiempo de uso hoy"
            )
            Spacer(Modifier.height(28.dp))
            StatBlock(
                value = formatDuration(data.avgUsageMillisLast7Days.toLong()),
                label = "Media tiempo de uso (últimos 7 días)"
            )
        }
    }
}

/** Reloj que se recompone solo al cambiar el minuto, sin sondear de más. */
@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            val millisToNextMinute = 60_000L - (now.timeInMillis % 60_000L)
            delay(millisToNextMinute)
        }
    }

    val timeText = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time) }
    val dateText = remember(now) {
        SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault())
            .format(now.time)
            .replaceFirstChar { it.uppercase() }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = timeText, fontSize = 72.sp, fontWeight = FontWeight.Thin, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            text = dateText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 40.sp, fontWeight = FontWeight.Light, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

private fun formatAvg(avg: Double): String = String.format("%.1f", avg)

private fun formatDuration(millis: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
}

/** Listado de apps en texto plano, con buscador, sin iconos. */
@Composable
private fun AppsScreen(onOpenStats: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            InstalledAppsProvider.getLaunchableApps(context)
        }
    }

    val filteredApps = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 40.dp)
    ) {
        Text(
            text = "Estadísticas",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier
                .clickable(onClick = onOpenStats)
                .padding(bottom = 16.dp)
        )
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar", color = Color.White.copy(alpha = 0.5f)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.White,
                unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f),
                cursorColor = Color.White
            )
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredApps, key = { it.packageName }) { app ->
                Text(
                    text = app.label,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(InstalledAppsProvider.launchIntentFor(app))
                        }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

/** Las 4 cifras de siempre + el desglose de uso de hoy por app, leído de Room. */
@Composable
private fun StatsDetailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var overview by remember { mutableStateOf<UsageOverview?>(null) }
    var breakdown by remember { mutableStateOf<List<AppUsageDisplay>>(emptyList()) }

    LaunchedEffect(Unit) {
        val (loadedOverview, loadedBreakdown) = withContext(Dispatchers.IO) {
            UsageStatsCalculator.calculate(context, db) to UsageStatsCalculator.getTodayBreakdown(context, db)
        }
        overview = loadedOverview
        breakdown = loadedBreakdown
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 40.dp)
    ) {
        Text(
            text = "← Volver",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(bottom = 24.dp)
        )

        val data = overview
        if (data == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            StatBlock(value = data.unlocksToday.toString(), label = "Desbloqueos hoy")
            Spacer(Modifier.height(20.dp))
            StatBlock(
                value = formatAvg(data.avgUnlocksLast7Days),
                label = "Media desbloqueos (últimos 7 días)"
            )
            Spacer(Modifier.height(20.dp))
            StatBlock(
                value = formatDuration(data.usageMillisToday),
                label = "Tiempo de uso hoy"
            )
            Spacer(Modifier.height(20.dp))
            StatBlock(
                value = formatDuration(data.avgUsageMillisLast7Days.toLong()),
                label = "Media tiempo de uso (últimos 7 días)"
            )

            Spacer(Modifier.height(36.dp))
            Text(
                text = "Uso de hoy por app",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(12.dp))

            if (breakdown.isEmpty()) {
                Text(
                    text = "Todavía no hay datos de hoy.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(breakdown, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = app.displayName,
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatDuration(app.durationMs),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
