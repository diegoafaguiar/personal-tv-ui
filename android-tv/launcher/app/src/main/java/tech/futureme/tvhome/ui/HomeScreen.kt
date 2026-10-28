package tech.futureme.tvhome.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import tech.futureme.tvhome.data.NewsContent
import tech.futureme.tvhome.data.NewsItem
import tech.futureme.tvhome.data.Profile
import tech.futureme.tvhome.data.QuickAction
import tech.futureme.tvhome.data.RatesRepository
import tech.futureme.tvhome.data.RecentEntry
import tech.futureme.tvhome.data.Section
import tech.futureme.tvhome.data.Settings
import tech.futureme.tvhome.data.TeamContent
import tech.futureme.tvhome.data.TunerChannel
import tech.futureme.tvhome.data.WeatherRepository
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    content: HomeContent,
    settings: Settings,
    profile: Profile,
    rates: List<RatesRepository.Rate>,
    weather: WeatherRepository.Weather?,
    team: TeamContent,
    focused: MediaItem?,
    onFocused: (MediaItem?) -> Unit,
    appLabel: (String) -> String,
    onOpenItem: (MediaItem) -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onOpenInput: (InputEntry) -> Unit,
    onOpenRecent: (RecentEntry) -> Unit,
    onOpenChannel: (TunerChannel) -> Unit,
    onOpenTuner: () -> Unit,
    onQuick: (QuickAction) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onSwitchProfile: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val visibleApps = content.apps.filter { it.packageName !in settings.hidden && profile.allows(it.packageName) }
    val byPkg = visibleApps.associateBy { it.packageName }
    val dock = settings.dock.mapNotNull { byPkg[it] }.ifEmpty { visibleApps.take(8) }
    val kids = profile.isKids
    fun List<MediaItem>.allowed() = filter { profile.allows(it.packageName) }
    val gap = LocalDims.current.gap
    val tuner = content.inputs.firstOrNull { it.isTuner && it.builtIn }
    val hdmi = content.inputs.filter { !it.isTuner && it.builtIn }
    val broadcast = content.tunerChannels.sortedBy { if (it.name.contains("globo", true)) -1 else 0 }
    val listState = rememberLazyListState()
    // Hero expandido só com a lista no topo; ao rolar para baixo ele colapsa, ao voltar reexpande.
    val collapsed by remember { derivedStateOf { settings.heroCompact || listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24 } }

    Column(Modifier.fillMaxSize()) {
        Hero(
            collapsed = collapsed,
            rates = if (settings.showRates) rates else emptyList(),
            weather = if (settings.showWeather) weather else null,
            status = if (settings.showStatus && !kids) content.status else null,
            focused = if (!collapsed) focused else null,
            appLabel = appLabel,
            who = profile.name,
        )
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 48.dp)) {
            // Menu nativo: só ícones
            item {
                LazyRow(contentPadding = sidePadding(), horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    item { IconButton(Icons.Rounded.Person, "Perfil: ${profile.name}", onSwitchProfile) }
                    if (!kids) item { IconButton(Icons.Rounded.Search, "Buscar", onOpenSearch) }
                    if (tuner != null || broadcast.isNotEmpty()) item { IconButton(Icons.Rounded.Tv, "TV aberta", onOpenTuner) }
                    if (!kids && settings.showInputs) items(hdmi, key = { it.id }) { IconButton(Icons.Rounded.SettingsInputHdmi, it.label, { onOpenInput(it) }) }
                    if (!kids) items(QuickAction.entries.filter { it in settings.quick }, key = { it.name }) { IconButton(it.icon, it.label, { onQuick(it) }) }
                    if (!kids) item { IconButton(Icons.Rounded.Apps, "Todos os apps", onOpenAllApps) }
                }
            }

            // Dock
            item {
                LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                    items(dock, key = { it.packageName }) { AppCard(it) { onOpenApp(it) } }
                }
            }

            homeRows(content, settings, profile, team, appLabel, onOpenItem, onOpenChannel, onOpenRecent, onFocused, onRequestPermission)

            if (!kids) item {
                RowTitle("Esta home")
                LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                    item { PillCard("Ajustes", onOpenSettings) }
                    item { PillCard("Diagnóstico", onOpenDiagnostics) }
                    item { PillCard("Trocar perfil", onSwitchProfile) }
                }
            }
        }
    }
}


/** Linhas de conteúdo compartilhadas pela home clássica e pela home de vidro. */
fun LazyListScope.homeRows(
    content: HomeContent,
    settings: Settings,
    profile: Profile,
    team: TeamContent,
    appLabel: (String) -> String,
    onOpenItem: (MediaItem) -> Unit,
    onOpenChannel: (TunerChannel) -> Unit,
    onOpenRecent: (RecentEntry) -> Unit,
    onFocused: (MediaItem?) -> Unit,
    onRequestPermission: () -> Unit,
    news: NewsContent = NewsContent(),
    onOpenNews: (NewsItem) -> Unit = {},
    onOpenYoutubeSearch: (String) -> Unit = {},
) {
    val kids = profile.isKids
    fun List<MediaItem>.allowed() = filter { profile.allows(it.packageName) }
    val gap = 12.dp
    val broadcast = content.tunerChannels.sortedBy { if (it.name.contains("globo", true)) -1 else 0 }
    if (!content.hasPermission) {
        item {
            RowTitle("Sem acesso ao que os apps publicam", "Conceda a permissão de listagens de TV para ver \"Continuar assistindo\" e as seções.")
            LazyRow(contentPadding = rowPadding()) { item { PillCard("Conceder permissão", onRequestPermission) } }
        }
    }

    val cont = content.sections[Section.CONTINUE].orEmpty().allowed()
    if (cont.isNotEmpty() && Section.CONTINUE.name !in settings.disabledSections) {
        item(key = "title-continue") { RowTitle(Section.CONTINUE.title, "o que cada app publicou") }
        item(key = "row-continue") { MediaRow(cont, appLabel, onOpenItem, onFocused) }
    }

    // Em alta: o que os streamings em que você está logado destacam, dos apps mais usados para os menos
    val hot = content.hot.allowed()
    if (hot.isNotEmpty() && Section.EM_ALTA.name !in settings.disabledSections) {
        item(key = "title-hot") {
            RowTitle(Section.EM_ALTA.title, if (content.usageRanked) "destaques dos seus streamings, dos que você mais usa primeiro" else "destaques dos seus streamings")
        }
        item(key = "row-hot") { MediaRow(hot, appLabel, onOpenItem, onFocused) }
    }

    // TV Globo (perfil Letícia): canal com sintonia + grade das próximas horas
    if (profile.showGlobo && content.globo != null) {
        val g = content.globo
        item(key = "title-globo") { RowTitle("TV Globo", g.nowTitle?.let { "Agora: $it" }) }
        item(key = "row-globo") {
            LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                item { ChannelCard(g) { onOpenChannel(g) } }
                val now = System.currentTimeMillis()
                items(content.globoSchedule.take(12), key = { "pg-${it.startMs}" }) { ProgramCard(it, isNow = it.startMs <= now && it.endMs > now) { onOpenChannel(g) } }
                items(content.perApp["com.globo.globotv"].orEmpty().take(10), key = { "gp-${it.id}" }) { MediaCard(it, "Globoplay", onFocused) { onOpenItem(it) } }
            }
        }
    }

    // Time: jogo/tabela, notícias, o que os apps publicam, vídeos (RSS do YouTube) e link para o YouTube
    if (profile.showTeam && settings.showTeam && (team.info != null || team.videos.isNotEmpty() || team.related.isNotEmpty() || news.team.isNotEmpty())) {
        item(key = "title-team") { RowTitle(settings.teamName, "jogo, tabela, notícias e vídeos") }
        item(key = "row-team") {
            LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                item { TeamInfoCard(settings.teamName, team.info) { onOpenYoutubeSearch(settings.teamName) } }
                items(news.team.take(8), key = { "news-" + it.link.hashCode() }) { NewsCard(it) { onOpenNews(it) } }
                items(team.related, key = { "rel-${it.packageName}:${it.id}" }) { MediaCard(it, appLabel(it.packageName), onFocused) { onOpenItem(it) } }
                items(team.videos, key = { "yt-${it.id}" }) { MediaCard(it, it.channelName ?: "YouTube", onFocused) { onOpenItem(it) } }
                item { PillCard("${settings.teamName} no YouTube", { onOpenYoutubeSearch(settings.teamName) }, sub = "vídeos e lances") }
            }
        }
    }

    // Lançamentos: estreias nos streamings instalados (atualiza toda vez que a TV liga)
    if (!kids && news.releases.isNotEmpty() && Section.LANCAMENTOS.name !in settings.disabledSections) {
        item(key = "title-releases") { RowTitle("Lançamentos", "estreias nos seus streamings") }
        item(key = "row-releases") {
            LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                items(news.releases, key = { "rel-n-" + it.link.hashCode() }) { NewsCard(it) { onOpenNews(it) } }
            }
        }
    }

    // Tecnologia: manchetes gerais e sobre os apps instalados (Netflix, Spotify, YouTube…)
    if (!kids && news.tech.isNotEmpty() && Section.TECNOLOGIA.name !in settings.disabledSections) {
        item(key = "title-tech") { RowTitle("Tecnologia", "novidades e os apps que você tem") }
        item(key = "row-tech") {
            LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                items(news.tech, key = { "tech-" + it.link.hashCode() }) { NewsCard(it) { onOpenNews(it) } }
            }
        }
    }

    val fut = content.sections[Section.FUTEBOL].orEmpty().allowed()
    if (!kids && (fut.isNotEmpty() || content.sportsChannels.isNotEmpty()) && Section.FUTEBOL.name !in settings.disabledSections) {
        item(key = "title-futebol") { RowTitle("Futebol") }
        item(key = "row-futebol") {
            LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                items(content.sportsChannels, key = { "ch-" + it.id }) { ChannelCard(it) { onOpenChannel(it) } }
                items(fut, key = { "${it.packageName}:${it.id}:${it.isWatchNext}" }) { MediaCard(it, appLabel(it.packageName), onFocused) { onOpenItem(it) } }
            }
        }
    }

    if (broadcast.isNotEmpty()) {
        item(key = "title-tv") { RowTitle("TV aberta") }
        item(key = "row-tv") {
            LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                items(broadcast, key = { it.id }) { ChannelCard(it) { onOpenChannel(it) } }
            }
        }
    }

    for (section in listOf(Section.FILMES, Section.SERIES, Section.OUTROS)) {
        if (section.name in settings.disabledSections) continue
        val sectionItems = content.sections[section].orEmpty().allowed()
        if (sectionItems.isEmpty()) continue
        item(key = "title-${section.name}") { RowTitle(section.title) }
        item(key = "row-${section.name}") { MediaRow(sectionItems, appLabel, onOpenItem, onFocused) }
    }

    if (!kids && settings.recents.isNotEmpty()) {
        item(key = "title-recents") { RowTitle("Abertos recentemente") }
        item(key = "row-recents") {
            LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                items(settings.recents, key = { it.encode() }) { r ->
                    PillCard(r.title.ifBlank { appLabel(r.packageName) }, { onOpenRecent(r) }, sub = appLabel(r.packageName))
                }
            }
        }
    }

    for ((pkg, appItems) in content.perApp) {
        if (appItems.isEmpty() || !profile.allows(pkg)) continue
        item(key = "title-app-$pkg") { RowTitle(appLabel(pkg)) }
        item(key = "row-app-$pkg") { MediaRow(appItems.take(30), appLabel, onOpenItem, onFocused) }
    }

    if (!kids && settings.showFast && content.fastChannels.isNotEmpty()) {
        item(key = "title-fast") { RowTitle("Canais pela internet") }
        item(key = "row-fast") {
            LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                items(content.fastChannels, key = { it.id }) { ChannelCard(it) { onOpenChannel(it) } }
            }
        }
    }

}

/**
 * Hero minimalista. Expandido: hora grande, data, clima e cotações à direita, item focado embaixo.
 * Colapsado: uma linha só (hora · data · clima · dólar · euro).
 */
@Composable
private fun Hero(
    collapsed: Boolean,
    rates: List<RatesRepository.Rate>,
    weather: WeatherRepository.Weather?,
    status: DeviceStatus?,
    focused: MediaItem?,
    appLabel: (String) -> String,
    who: String,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    val locale = Locale("pt", "BR")
    val time = remember(now) { SimpleDateFormat("HH:mm", locale).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d 'de' MMMM", locale).format(now).replaceFirstChar { it.uppercase() } }
    val money = NumberFormat.getCurrencyInstance(locale)

    Column(Modifier.fillMaxWidth().padding(sidePadding()).padding(top = if (collapsed) 18.dp else 36.dp, bottom = 10.dp)) {
        if (collapsed) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(time, fontSize = 26.sp, fontWeight = FontWeight.Light, color = TvColors.text)
                    Text(date, fontSize = 14.sp, color = TvColors.textDim)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (weather != null) Text("${weather.icon} ${weather.tempC.toInt()}°", fontSize = 14.sp, color = TvColors.textDim)
                    for (r in rates) Text("${label(r.code)} ${money.format(r.bid)} ${arrow(r.pctChange)}", fontSize = 14.sp, color = TvColors.textDim)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("Olá, $who", fontSize = 15.sp, color = TvColors.textDim)
                    Text(time, fontSize = 68.sp, fontWeight = FontWeight.ExtraLight, color = TvColors.text)
                    Text(date, fontSize = 17.sp, color = TvColors.textDim)
                    if (status != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(statusLine(status, locale), fontSize = 12.sp, color = TvColors.textDim.copy(alpha = 0.7f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(if (LocalGlass.current.config.enabled) 12.dp else 40.dp), verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 14.dp)) {
                    if (weather != null) Stat("${weather.icon} ${weather.tempC.toInt()}°", "${weather.label} · ${weather.minC.toInt()}°/${weather.maxC.toInt()}°")
                    for (r in rates) Stat(money.format(r.bid), "${label(r.code)} ${arrow(r.pctChange)} ${String.format(locale, "%.2f%%", kotlin.math.abs(r.pctChange))}", arrowColor(r.pctChange))
                }
            }
            AnimatedVisibility(visible = focused != null) {
                if (focused != null) {
                    Column(Modifier.width(680.dp).padding(top = 18.dp)) {
                        Text(focused.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(appLabel(focused.packageName), focused.episodeTitle).joinToString("  ·  "), fontSize = 13.sp, color = TvColors.textDim)
                        if (!focused.description.isNullOrBlank()) Text(focused.description, fontSize = 14.sp, color = TvColors.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, caption: String, captionColor: Color = TvColors.textDim) {
    val glassOn = LocalGlass.current.config.enabled
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = if (glassOn) Alignment.Start else Alignment.End,
        modifier = if (glassOn) Modifier.glass(shape).padding(horizontal = 16.dp, vertical = 10.dp) else Modifier,
    ) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Light, color = TvColors.text)
        Text(caption, fontSize = 12.sp, color = captionColor)
    }
}

private fun label(code: String) = when (code) { "USD" -> "Dólar"; "EUR" -> "Euro"; else -> code }
private fun arrow(pct: Double) = if (pct > 0) "▲" else if (pct < 0) "▼" else "•"
private fun arrowColor(pct: Double) = changeColor(pct)
private fun statusLine(status: DeviceStatus, locale: Locale) = buildList {
    add(status.network)
    status.bluetoothOn?.let { add(if (it) "Bluetooth ligado" else "Bluetooth desligado") }
    if (status.freeRamMb > 0) add("${status.freeRamMb} MB livres")
    if (status.freeStorageGb > 0) add(String.format(locale, "%.1f GB livres", status.freeStorageGb))
}.joinToString("   ·   ")
