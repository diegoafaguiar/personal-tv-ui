package tech.futureme.tvhome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.DeviceStatus
import tech.futureme.tvhome.data.HomeContent
import tech.futureme.tvhome.data.InputEntry
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.data.QuickAction
import tech.futureme.tvhome.data.RatesRepository
import tech.futureme.tvhome.data.RecentEntry
import tech.futureme.tvhome.data.Section
import tech.futureme.tvhome.data.Settings
import tech.futureme.tvhome.data.TunerChannel
import tech.futureme.tvhome.data.WeatherRepository
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    content: HomeContent,
    settings: Settings,
    rates: List<RatesRepository.Rate>,
    weather: WeatherRepository.Weather?,
    focused: MediaItem?,
    onFocused: (MediaItem?) -> Unit,
    appLabel: (String) -> String,
    onOpenItem: (MediaItem) -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onOpenInput: (InputEntry) -> Unit,
    onOpenRecent: (RecentEntry) -> Unit,
    onOpenChannel: (TunerChannel) -> Unit,
    onQuick: (QuickAction) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val visibleApps = content.apps.filter { it.packageName !in settings.hidden }
    val byPkg = visibleApps.associateBy { it.packageName }
    val dock = settings.dock.mapNotNull { byPkg[it] }.ifEmpty { visibleApps.take(8) }
    val gap = LocalDims.current.gap

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp)) {
        item {
            Hero(
                rates = if (settings.showRates) rates else emptyList(),
                weather = if (settings.showWeather) weather else null,
                status = content.status,
                focused = focused,
                appLabel = appLabel,
            )
        }

        // Dock: busca, TV ao vivo + apps favoritos
        item {
            LazyRow(contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                item { PillCard("Buscar", onOpenSearch, sub = "em todos os apps") }
                items(content.inputs.filter { it.isTuner }, key = { it.id }) { InputCard(it) { onOpenInput(it) } }
                items(dock, key = { it.packageName }) { AppCard(it) { onOpenApp(it) } }
                item { PillCard("Todos os apps", onOpenAllApps, sub = "${visibleApps.size} instalados") }
            }
        }

        // Atalhos: HDMI 1/2/3 e funções rápidas
        if (settings.showInputs || settings.quick.isNotEmpty()) {
            item { Spacer(Modifier.height(gap)) }
            item {
                LazyRow(contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    if (settings.showInputs) items(content.inputs.filter { !it.isTuner }, key = { it.id }) { InputCard(it) { onOpenInput(it) } }
                    items(QuickAction.entries.filter { it in settings.quick }, key = { it.name }) { PillCard(it.label, { onQuick(it) }) }
                }
            }
        }

        if (!content.hasPermission) {
            item {
                RowTitle("Sem acesso ao que os apps publicam", "Conceda a permissão de listagens de TV para ver \"Continuar assistindo\" e as seções.")
                LazyRow(contentPadding = rowPadding()) { item { PillCard("Conceder permissão", onRequestPermission) } }
            }
        }

        // TV aberta: canais do sintonizador com o programa de agora
        if (content.tunerChannels.isNotEmpty()) {
            item(key = "title-tv") { RowTitle("TV aberta", "${content.tunerChannels.size} canais · toque para sintonizar") }
            item(key = "row-tv") {
                LazyRow(contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items(content.tunerChannels, key = { it.id }) { ch ->
                        PillCard("${ch.number} ${ch.name}", { onOpenChannel(ch) }, sub = ch.nowTitle ?: "sem grade")
                    }
                }
            }
        }

        if (settings.recents.isNotEmpty()) {
            item(key = "title-recents") { RowTitle("Abertos recentemente") }
            item(key = "row-recents") {
                LazyRow(contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items(settings.recents, key = { it.encode() }) { r ->
                        PillCard(r.title.ifBlank { appLabel(r.packageName) }, { onOpenRecent(r) }, sub = appLabel(r.packageName))
                    }
                }
            }
        }

        for (section in Section.entries) {
            if (section.name in settings.disabledSections) continue
            val sectionItems = content.sections[section].orEmpty()
            if (sectionItems.isEmpty()) continue
            item(key = "title-${section.name}") {
                RowTitle(section.title, "${sectionItems.size} itens de ${sectionItems.map { it.packageName }.distinct().size} apps")
            }
            item(key = "row-${section.name}") { MediaRow(sectionItems, appLabel, onOpenItem, onFocused) }
        }

        // Sugestões por app, para descoberta dentro de cada serviço
        val perApp = content.perApp.entries.sortedByDescending { it.value.size }
        for ((pkg, appItems) in perApp) {
            if (appItems.isEmpty()) continue
            item(key = "title-app-$pkg") { RowTitle("Sugestões · ${appLabel(pkg)}") }
            item(key = "row-app-$pkg") { MediaRow(appItems.take(30), appLabel, onOpenItem, onFocused) }
        }

        item {
            RowTitle("Esta home")
            LazyRow(contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                item { PillCard("Ajustes da home", onOpenSettings) }
                item { PillCard("Diagnóstico", onOpenDiagnostics) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Saudação, hora, data, estado da TV; à direita clima e cotações; abaixo, o item focado. */
@Composable
private fun Hero(
    rates: List<RatesRepository.Rate>,
    weather: WeatherRepository.Weather?,
    status: DeviceStatus,
    focused: MediaItem?,
    appLabel: (String) -> String,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    val locale = Locale("pt", "BR")
    val time = remember(now) { SimpleDateFormat("HH:mm", locale).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d 'de' MMMM", locale).format(now).replaceFirstChar { it.uppercase() } }
    val greeting = remember(now) {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    }
    val statusLine = remember(status) {
        buildList {
            add(status.network)
            status.bluetoothOn?.let { add(if (it) "Bluetooth ligado" else "Bluetooth desligado") }
            if (status.freeRamMb > 0) add("${status.freeRamMb} MB livres")
            if (status.freeStorageGb > 0) add(String.format(locale, "%.1f GB de espaço", status.freeStorageGb))
        }.joinToString("  ·  ")
    }
    Column(Modifier.fillMaxWidth().padding(rowPadding()).padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(greeting, fontSize = 18.sp, color = TvColors.textDim)
                Text(time, fontSize = 48.sp, fontWeight = FontWeight.Light, color = TvColors.text)
                Text(date, fontSize = 17.sp, color = TvColors.textDim)
                Text(statusLine, fontSize = 13.sp, color = TvColors.textDim)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                if (weather != null) WeatherChip(weather)
                for (r in rates) RateChip(r)
            }
        }
        if (focused != null) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.width(620.dp)) {
                Text(focused.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(appLabel(focused.packageName), focused.episodeTitle, focused.genres.firstOrNull()).joinToString(" · ")
                Text(meta, fontSize = 14.sp, color = TvColors.accent)
                if (!focused.description.isNullOrBlank()) {
                    Text(focused.description, fontSize = 15.sp, color = TvColors.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun WeatherChip(w: WeatherRepository.Weather) {
    Column(horizontalAlignment = Alignment.End) {
        Text("Recreio · ${w.label}", fontSize = 14.sp, color = TvColors.textDim)
        Text("${w.icon} ${w.tempC.toInt()}°", fontSize = 26.sp, fontWeight = FontWeight.Medium, color = TvColors.text)
        Text("${w.minC.toInt()}° / ${w.maxC.toInt()}°  ·  chuva ${w.rainPct}%", fontSize = 14.sp, color = TvColors.textDim)
    }
}

@Composable
private fun RateChip(rate: RatesRepository.Rate) {
    val money = remember(rate.bid) { NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(rate.bid) }
    val pct = remember(rate.pctChange) { String.format(Locale("pt", "BR"), "%+.2f%%", rate.pctChange) }
    val color = if (rate.pctChange > 0) Color(0xFFE57373) else if (rate.pctChange < 0) Color(0xFF81C784) else TvColors.textDim
    Column(horizontalAlignment = Alignment.End) {
        Text(if (rate.code == "USD") "Dólar" else if (rate.code == "EUR") "Euro" else rate.code, fontSize = 14.sp, color = TvColors.textDim)
        Text(money, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = TvColors.text)
        Text(pct, fontSize = 14.sp, color = color)
    }
}
