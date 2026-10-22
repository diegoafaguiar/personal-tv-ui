package tech.futureme.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.Section
import tech.futureme.tvhome.data.Settings

@Composable
fun SettingsScreen(
    apps: List<AppEntry>,
    settings: Settings,
    onToggleDock: (String) -> Unit,
    onMoveDock: (String, Int) -> Unit,
    onToggleHidden: (String) -> Unit,
    onToggleSection: (Section) -> Unit,
    onSetLiveTv: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().background(TvColors.background), contentPadding = PaddingValues(vertical = 28.dp)) {
        item { RowTitle("Ajustes da home", "Voltar sai desta tela. As mudanças valem na hora.") }

        item { RowTitle("Seções") }
        items(Section.entries) { section ->
            val off = section.name in settings.disabledSections
            SettingRow(label = section.title, value = if (off) "oculta" else "visível") { onToggleSection(section) }
        }

        item { RowTitle("Dock", "OK adiciona/remove; as setas mudam a ordem") }
        items(apps, key = { "dock-" + it.packageName }) { app ->
            val inDock = app.packageName in settings.dock
            val pos = settings.dock.indexOf(app.packageName)
            Row(
                Modifier.fillMaxWidth().padding(RowPadding).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingRow(label = app.label, value = if (inDock) "no dock (${pos + 1})" else "fora", modifier = Modifier.width(520.dp)) { onToggleDock(app.packageName) }
                if (inDock) {
                    PillSmall("◀") { onMoveDock(app.packageName, -1) }
                    PillSmall("▶") { onMoveDock(app.packageName, +1) }
                }
            }
        }

        item { RowTitle("Apps ocultos", "Somem do dock e de \"Todos os apps\"") }
        items(apps, key = { "hide-" + it.packageName }) { app ->
            val hidden = app.packageName in settings.hidden
            SettingRow(label = app.label, value = if (hidden) "oculto" else "visível") { onToggleHidden(app.packageName) }
        }

        item { RowTitle("App de TV ao vivo", "Quem abre quando você escolhe o sintonizador (atual: ${settings.liveTvPackage})") }
        items(apps.filter { it.packageName.contains("tv", ignoreCase = true) }, key = { "live-" + it.packageName }) { app ->
            SettingRow(label = app.label, value = app.packageName) { onSetLiveTv(app.packageName) }
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth().padding(RowPadding).padding(vertical = 4.dp),
    onClick: () -> Unit,
) {
    FocusSurface(onClick = onClick, modifier = modifier.height(56.dp)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 20.sp, color = TvColors.text)
            Text(value, fontSize = 16.sp, color = TvColors.textDim)
        }
    }
}

@Composable
private fun PillSmall(label: String, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = Modifier.width(56.dp).height(56.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 20.sp, color = TvColors.text)
        }
    }
}
