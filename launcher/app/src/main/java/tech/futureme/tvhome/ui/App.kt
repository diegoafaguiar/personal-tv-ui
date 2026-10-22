package tech.futureme.tvhome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tech.futureme.tvhome.HomeViewModel
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.InputEntry
import tech.futureme.tvhome.data.MediaItem

private enum class Screen { HOME, ALL_APPS, SETTINGS, DIAGNOSTICS }

@Composable
fun App(
    vm: HomeViewModel,
    onOpenItem: (MediaItem) -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onOpenInput: (InputEntry) -> Unit,
    onOpenSettingsApp: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val content by vm.content.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.HOME) }
    val scope = rememberCoroutineScope()
    val appLabel: (String) -> String = { pkg -> content.apps.firstOrNull { it.packageName == pkg }?.label ?: vm.appsRepo.label(pkg) }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    when (screen) {
        Screen.HOME -> HomeScreen(
            content = content, settings = settings, appLabel = appLabel,
            onOpenItem = onOpenItem, onOpenApp = onOpenApp, onOpenInput = onOpenInput,
            onOpenAllApps = { screen = Screen.ALL_APPS },
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
            onOpenSettingsApp = onOpenSettingsApp,
            onRequestPermission = onRequestPermission,
        )
        Screen.ALL_APPS -> LazyVerticalGrid(
            columns = GridCells.Adaptive(216.dp),
            modifier = Modifier.fillMaxSize().background(TvColors.background),
            contentPadding = PaddingValues(48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
        )
        Screen.DIAGNOSTICS -> DiagnosticsScreen(content, appLabel)
    }
}
