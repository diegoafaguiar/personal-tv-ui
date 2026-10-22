package tech.futureme.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.HomeContent
import tech.futureme.tvhome.data.InputEntry
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.data.Section
import tech.futureme.tvhome.data.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    content: HomeContent,
    settings: Settings,
    appLabel: (String) -> String,
    onOpenItem: (MediaItem) -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onOpenInput: (InputEntry) -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettingsApp: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val visibleApps = content.apps.filter { it.packageName !in settings.hidden }
    val byPkg = visibleApps.associateBy { it.packageName }
    val dock = settings.dock.mapNotNull { byPkg[it] }.ifEmpty { visibleApps.take(8) }

    LazyColumn(Modifier.fillMaxSize().background(TvColors.background), contentPadding = PaddingValues(vertical = 28.dp)) {
        item { Header() }

        // Dock: TV ao vivo, apps favoritos, entradas HDMI
        item {
            LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(content.inputs.filter { it.isTuner }, key = { it.id }) { InputCard(it) { onOpenInput(it) } }
                items(dock, key = { it.packageName }) { AppCard(it) { onOpenApp(it) } }
                items(content.inputs.filter { !it.isTuner }, key = { it.id }) { InputCard(it) { onOpenInput(it) } }
                item { PillCard("Todos os apps", onOpenAllApps) }
            }
        }

        if (!content.hasPermission) {
            item {
                RowTitle("Sem acesso ao que os apps publicam", "Conceda a permissão de listagens de TV para ver \"Continuar assistindo\" e as seções.")
                LazyRow(contentPadding = RowPadding) { item { PillCard("Conceder permissão", onRequestPermission) } }
            }
        }

        for (section in Section.entries) {
            if (section.name in settings.disabledSections) continue
            val sectionItems = content.sections[section].orEmpty()
            if (sectionItems.isEmpty()) continue
            item(key = "title-${section.name}") {
                RowTitle(section.title, "${sectionItems.size} itens de ${sectionItems.map { it.packageName }.distinct().size} apps")
            }
            item(key = "row-${section.name}") { MediaRow(sectionItems, appLabel, onOpenItem) }
        }

        // Sugestões por app, para descoberta dentro de cada serviço
        val perApp = content.perApp.entries.sortedByDescending { it.value.size }
        for ((pkg, appItems) in perApp) {
            if (appItems.isEmpty()) continue
            item(key = "title-app-$pkg") { RowTitle("Sugestões · ${appLabel(pkg)}") }
            item(key = "row-app-$pkg") { MediaRow(appItems.take(30), appLabel, onOpenItem) }
        }

        item {
            RowTitle("Sistema")
            LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                item { PillCard("Configurações da TV", onOpenSettingsApp) }
                item { PillCard("Ajustes da home", onOpenSettings) }
                item { PillCard("Diagnóstico", onOpenDiagnostics) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Header() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(30_000) } }
    val locale = Locale("pt", "BR")
    val time = remember(now) { SimpleDateFormat("HH:mm", locale).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d 'de' MMMM", locale).format(now).replaceFirstChar { it.uppercase() } }
    Row(Modifier.fillMaxWidth().padding(RowPadding).padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Column {
            Text(time, fontSize = 44.sp, fontWeight = FontWeight.Light, color = TvColors.text)
            Text(date, fontSize = 18.sp, color = TvColors.textDim)
        }
    }
}
