package tech.futureme.tvhome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tech.futureme.tvhome.HomeViewModel
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.Background
import tech.futureme.tvhome.data.InputEntry
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.data.QuickAction
import tech.futureme.tvhome.data.RecentEntry
import tech.futureme.tvhome.data.TunerChannel
import java.io.File
import java.util.Calendar

private enum class Screen { HOME, ALL_APPS, SETTINGS, DIAGNOSTICS, SEARCH }

@Composable
fun App(
    vm: HomeViewModel,
    onOpenItem: (MediaItem) -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onOpenInput: (InputEntry) -> Unit,
    onOpenRecent: (RecentEntry) -> Unit,
    onOpenChannel: (TunerChannel) -> Unit,
    onQuick: (QuickAction) -> Unit,
    onRequestPermission: () -> Unit,
) {
    val content by vm.content.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rates by vm.rates.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var focused by remember { mutableStateOf<MediaItem?>(null) }
    val scope = rememberCoroutineScope()
    val appLabel: (String) -> String = { pkg -> content.apps.firstOrNull { it.packageName == pkg }?.label ?: vm.appsRepo.label(pkg) }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    CompositionLocalProvider(LocalDims provides Dims.of(settings.density)) {
        Box(Modifier.fillMaxSize()) {
            BackgroundLayer(settings.background, if (screen == Screen.HOME) focused?.imageUri else null, settings.nightMode)
            when (screen) {
                Screen.HOME -> HomeScreen(
                    content = content, settings = settings, rates = rates, weather = weather,
                    focused = focused, onFocused = { focused = it }, appLabel = appLabel,
                    onOpenItem = onOpenItem, onOpenApp = onOpenApp, onOpenInput = onOpenInput,
                    onOpenRecent = onOpenRecent, onOpenChannel = onOpenChannel, onQuick = onQuick,
                    onOpenSearch = { screen = Screen.SEARCH },
                    onOpenAllApps = { screen = Screen.ALL_APPS },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                    onRequestPermission = onRequestPermission,
                )
                Screen.ALL_APPS -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(LocalDims.current.appW + 16.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(LocalDims.current.side),
                    horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap),
                    verticalArrangement = Arrangement.spacedBy(LocalDims.current.gap),
                ) {
                    items(content.apps.filter { it.packageName !in settings.hidden }, key = { it.packageName }) { app ->
                        AppCard(app) { onOpenApp(app) }
                    }
                }
                Screen.SETTINGS -> SettingsScreen(
                    apps = content.apps, settings = settings,
                    onToggleDock = { scope.launch { vm.prefs.toggleDock(it) } },
                    onMoveDock = { pkg, d -> scope.launch { vm.prefs.moveDock(pkg, d) } },
                    onToggleHidden = { scope.launch { vm.prefs.toggleHidden(it) } },
                    onToggleSection = { scope.launch { vm.prefs.toggleSection(it) } },
                    onSetLiveTv = { scope.launch { vm.prefs.setLiveTv(it) } },
                    onSetBackground = { scope.launch { vm.prefs.setBackground(it) } },
                    onToggleDensity = { scope.launch { vm.prefs.toggleDensity() } },
                    onToggleQuick = { scope.launch { vm.prefs.toggleQuick(it) } },
                    onToggleRates = { scope.launch { vm.prefs.toggleRates() } },
                    onToggleInputs = { scope.launch { vm.prefs.toggleInputs() } },
                    onToggleWeather = { scope.launch { vm.prefs.toggleWeather() } },
                    onToggleNight = { scope.launch { vm.prefs.toggleNight() } },
                    onClearRecents = { scope.launch { vm.prefs.clearRecents() } },
                    onToggleFast = { scope.launch { vm.prefs.toggleFast() } },
                )
                Screen.DIAGNOSTICS -> DiagnosticsScreen(content, appLabel)
                Screen.SEARCH -> SearchScreen(
                    items = content.allItems, apps = content.apps.filter { it.packageName !in settings.hidden },
                    appLabel = appLabel, onOpenItem = onOpenItem, onOpenApp = onOpenApp,
                )
            }
        }
    }
}

/**
 * Fundo da home: cor, gradiente ou a imagem em files/wallpaper.jpg (enviada por adb).
 * Com um item focado, a arte dele aparece por cima, escurecida. À noite, escurece mais.
 */
@Composable
private fun BackgroundLayer(bg: Background, focusedImage: String?, nightMode: Boolean) {
    val context = LocalContext.current
    when (bg) {
        Background.PRETO -> Box(Modifier.fillMaxSize().background(Color.Black))
        Background.GRAFITE -> Box(Modifier.fillMaxSize().background(Color(0xFF14171C)))
        Background.NOITE -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF0B0F14)))))
        Background.GRADIENTE -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B1F3A), Color(0xFF0B0F14), Color(0xFF10233A)))))
        Background.IMAGEM -> {
            val file = remember { File(context.getExternalFilesDir(null), "wallpaper.jpg") }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (file.exists()) {
                    AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    // Escurece para os textos continuarem legíveis
                    Box(Modifier.fillMaxSize().background(Color(0x80000000)))
                }
            }
        }
    }
    if (focusedImage != null) {
        AsyncImage(model = focusedImage, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000), 1f to Color(0xEE0B0F14))))
    }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    if (nightMode && (hour >= 21 || hour < 6)) Box(Modifier.fillMaxSize().background(Color(0x55000000)))
}
