@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package tech.futureme.tvhome.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wifi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tech.futureme.tvhome.data.AppEntry
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
import kotlin.math.roundToInt

/** Destinos da barra lateral. */
enum class Nav(val label: String, val icon: ImageVector, val kids: Boolean) {
    INICIO("Início", Icons.Rounded.Home, true),
    APPS("Apps", Icons.Rounded.Apps, true),
    FILMES("Explorar", Icons.Rounded.Explore, true),
    TV("TV ao Vivo", Icons.Rounded.LiveTv, true),
    JOGOS("Jogos", Icons.Rounded.SportsEsports, false),
    MUSICA("Música", Icons.Rounded.MusicNote, false),
    CONFIG("Configurações", Icons.Rounded.Settings, false),
}

private const val CATEGORY_GAME = 0      // ApplicationInfo.CATEGORY_GAME
private const val CATEGORY_AUDIO = 1     // ApplicationInfo.CATEGORY_AUDIO
private val MUSIC_PACKAGES = setOf("com.spotify.tv.android", "com.google.android.youtube.tvmusic", "com.amazon.tv.music", "deezer.android.tv", "com.apple.android.music")

fun AppEntry.isGame() = category == CATEGORY_GAME
fun AppEntry.isMusic() = category == CATEGORY_AUDIO || packageName in MUSIC_PACKAGES

/**
 * Home de vidro: relógio e atalhos no topo, barra lateral de navegação (colapsa em ícones,
 * expande no foco), hero em carrossel com o que os apps publicam, widgets de clima e entradas
 * à direita, "Seus aplicativos" em ícones quadrados e as linhas de conteúdo compartilhadas.
 */
@Composable
fun GlassHomeScreen(
    content: HomeContent,
    settings: Settings,
    profile: Profile,
    weather: WeatherRepository.Weather?,
    rates: List<RatesRepository.Rate>,
    team: TeamContent,
    news: NewsContent,
    onOpenNews: (NewsItem) -> Unit,
    onOpenYoutubeSearch: (String) -> Unit,
    appLabel: (String) -> String,
    onFocused: (MediaItem?) -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onOpenInput: (InputEntry) -> Unit,
    onOpenRecent: (RecentEntry) -> Unit,
    onOpenChannel: (TunerChannel) -> Unit,
    onOpenTuner: () -> Unit,
    onQuick: (QuickAction) -> Unit,
    onNav: (Nav) -> Unit,
    onOpenSearch: () -> Unit,
    onSwitchProfile: () -> Unit,
    onRequestPermission: () -> Unit,
    /** Muda a cada Home pressionado: volta a lista e a linha de apps ao começo e foca o primeiro app. */
    resetKey: Int = 0,
    categories: Map<Category, List<MediaItem>> = emptyMap(),
    onOpenCategory: (Category) -> Unit = {},
    onOpenRates: () -> Unit = {},
) {
    val kids = profile.isKids
    val visibleApps = content.apps.filter { it.packageName !in settings.hidden && profile.allows(it.packageName) }
    val byPkg = visibleApps.associateBy { it.packageName }
    val dock = settings.dock.mapNotNull { byPkg[it] }.ifEmpty { visibleApps.take(8) }
    val yourApps = dock + visibleApps.filter { it !in dock }
    val hdmi = content.inputs.filter { !it.isTuner && it.builtIn }
    val side = LocalDims.current.side

    // Carrossel: o que tem imagem, dos apps mais bem ranqueados primeiro
    val featured = remember(content, settings.appOrder) {
        (content.sections[Section.CONTINUE].orEmpty() + content.sections[Section.OUTROS].orEmpty() + content.sections[Section.SERIES].orEmpty() + content.sections[Section.FILMES].orEmpty())
            .filter { it.imageUri != null && profile.allows(it.packageName) }
            .distinctBy { it.packageName + it.title }
            .sortedBy { settings.appRank(it.packageName) }
            .take(6)
    }

    // Carrossel: conteúdo + entradas HDMI (adultos), para chegar às entradas sem sair do banner
    val slides = remember(featured, hdmi, kids) {
        featured.map<MediaItem, HeroSlide> { HeroSlide.Media(it) } + if (kids) emptyList() else hdmi.map { HeroSlide.Input(it) }
    }
    val firstApp = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val appsRow = rememberLazyListState()
    LaunchedEffect(yourApps.isNotEmpty(), resetKey) {
        runCatching { listState.scrollToItem(0); appsRow.scrollToItem(0) }
        delay(150)
        runCatching { if (yourApps.isNotEmpty()) firstApp.requestFocus() else heroFocus.requestFocus() }
    }
    // Voltar com a lista rolada: volta ao topo e ao primeiro app (padrão das homes de TV)
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }
    BackHandler(enabled = scrolled) {
        scope.launch { listState.animateScrollToItem(0); delay(80); runCatching { firstApp.requestFocus() } }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(kids = kids, who = profile.name, rates = if (settings.showRates) rates else emptyList(), onOpenSearch = onOpenSearch, onQuick = onQuick, onSwitchProfile = onSwitchProfile, onOpenRates = onOpenRates)
        Row(Modifier.fillMaxSize()) {
            Sidebar(kids = kids, selected = Nav.INICIO, onNav = onNav)
            // A lateral já ocupa a zona segura da esquerda: as linhas começam a 16 dp dela.
            val rowFocus = remember { RowFocusState() }
            val look = LocalLook.current
            val defaultSpec = LocalBringIntoViewSpec.current
            // "Linha focada fixa": alinha o topo da linha focada a 12% da altura, em vez do mínimo necessário
            val fixedSpec = remember {
                object : BringIntoViewSpec {
                    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - containerSize * 0.12f
                }
            }
            CompositionLocalProvider(LocalRowStart provides 16.dp, LocalRowFocus provides rowFocus, LocalBringIntoViewSpec provides if (look.fixedFocusRow) fixedSpec else defaultSpec) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = if (look.fixedFocusRow) 600.dp else 40.dp)) {
                  // As linhas horizontais voltam ao comportamento normal (só a lista vertical fica "fixa")
                  StyledRows(this, defaultSpec).run {
                    item(key = "hero") {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = side, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Hero(slides, appLabel, onFocused, onOpenItem, onOpenInput, modifier = Modifier.weight(1f).height(196.dp).focusRequester(heroFocus))
                            // Clima com previsão de 3 dias em cima; entrada HDMI / TV aberta numa faixa embaixo
                            Column(Modifier.width(264.dp).height(196.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                WeatherCard(weather, settings.showWeather, Modifier.weight(1f).fillMaxWidth())
                                if (!kids && hdmi.isNotEmpty()) InputsCard(hdmi, onOpenInput, Modifier.height(54.dp))
                                else if (content.tunerChannels.isNotEmpty() || content.inputs.any { it.isTuner }) TunerCard(onOpenTuner, Modifier.height(54.dp))
                            }
                        }
                    }
                    if (yourApps.isNotEmpty()) item(key = "apps-title") { RowTitle("Seus aplicativos") }
                    if (yourApps.isNotEmpty()) item(key = "apps") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                        LazyRow(Modifier.rememberFocus(), state = appsRow, contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                            itemsIndexed(yourApps, key = { _, a -> a.packageName }) { i, app ->
                                // Foco inicial da home: o primeiro app (não o ícone de perfil nem a lateral)
                                val m = if (i == 0) Modifier.focusRequester(firstApp) else Modifier
                                if (look.appTile == tech.futureme.tvhome.data.AppTile.BANNER) AppCard(app, modifier = m) { onOpenApp(app) }
                                else AppIcon(app, modifier = m) { onOpenApp(app) }
                            }
                        }
                        }
                    }
                    if (categories.size > 1) {
                        item(key = "title-categorias") { RowTitle("Explorar por categoria", "conteúdo de todos os apps", icon = Icons.Rounded.Explore) }
                        item(key = "row-categorias") { CategoryStrip(categories, onOpenCategory) }
                    }
                    homeRows(content, settings, profile, team, appLabel, onOpenItem, onOpenChannel, onOpenRecent, onFocused, onRequestPermission, news, onOpenNews, onOpenYoutubeSearch)
                  }
                }
            }
        }
    }
}

@Composable
private fun TopBar(kids: Boolean, who: String, rates: List<RatesRepository.Rate>, onOpenSearch: () -> Unit, onQuick: (QuickAction) -> Unit, onSwitchProfile: () -> Unit, onOpenRates: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    val seconds = LocalLook.current.clockSeconds
    LaunchedEffect(seconds) { while (true) { now = Date(); delay(if (seconds) 1_000 else 15_000) } }
    val locale = Locale("pt", "BR")
    val time = remember(now, seconds) { SimpleDateFormat(if (seconds) "HH:mm:ss" else "HH:mm", locale).format(now) }
    val date = remember(now) { SimpleDateFormat("EEE, d 'de' MMMM", locale).format(now).replaceFirstChar { it.uppercase() } }
    // Saudação pela hora: bom dia (5h–12h), boa tarde (12h–18h), boa noite
    val greeting = if (!LocalLook.current.greeting) null else remember(now, who) {
        val h = java.util.Calendar.getInstance().apply { this.time = now }.get(java.util.Calendar.HOUR_OF_DAY)
        (if (h in 5..11) "Bom dia" else if (h in 12..17) "Boa tarde" else "Boa noite") + ", $who"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = LocalDims.current.side, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                Text(time, fontSize = 34.sp, fontWeight = FontWeight.Light, color = TvColors.text)
                Text(listOfNotNull(greeting, date).joinToString(" · "), fontSize = 13.sp, color = TvColors.textDim, maxLines = 1)
            }
            // Cotações sempre à vista (dólar primeiro), em chips de vidro; OK abre a página do câmbio
            val money = NumberFormat.getCurrencyInstance(locale)
            for (r in rates.sortedBy { if (it.code == "USD") 0 else 1 }) {
                FocusSurface(onClick = onOpenRates, shape = RoundedCornerShape(14.dp), focusScale = 1.06f, modifier = Modifier.width(124.dp).height(50.dp)) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
                        Text(money.format(r.bid), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = TvColors.text)
                        Text(
                            "${r.label} ${if (r.pctChange > 0) "▲" else if (r.pctChange < 0) "▼" else "•"} ${String.format(locale, "%.2f%%", kotlin.math.abs(r.pctChange))}",
                            fontSize = 12.sp, color = changeColor(r.pctChange),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!kids) IconButton(Icons.Rounded.Search, "Buscar", onOpenSearch)
            if (!kids) IconButton(Icons.Rounded.Wifi, "Rede") { onQuick(QuickAction.REDE) }
            IconButton(Icons.Rounded.Person, "Perfil: $who", onSwitchProfile)
        }
    }
}

/** Barra lateral: ícones; com foco dentro dela, mostra os rótulos. [selected] fica marcado (tela atual). */
@Composable
fun Sidebar(kids: Boolean, selected: Nav?, onNav: (Nav) -> Unit, rightTarget: FocusRequester? = null) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val reqs = remember { Nav.entries.associateWith { FocusRequester() } }
    Column(
        Modifier.padding(start = LocalDims.current.side - 8.dp, top = 4.dp, bottom = 16.dp)
            // Ao entrar na lateral (◀), o foco cai na página atual, não no item mais próximo
            .focusProperties { enter = { reqs[selected ?: Nav.INICIO] ?: FocusRequester.Default } }
            .focusGroup()
            // → sai da lateral direto para o conteúdo (sem depender da geometria da tela)
            .then(if (rightTarget != null) Modifier.focusProperties { right = rightTarget } else Modifier)
            .glass(shape)
            .animateContentSize(tween(220))
            .width(if (expanded) 184.dp else 56.dp)
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (nav in Nav.entries) {
            if (kids && !nav.kids) continue
            NavItem(nav, expanded, selected = nav == selected, modifier = Modifier.focusRequester(reqs.getValue(nav))) { onNav(nav) }
        }
    }
}

@Composable
private fun NavItem(nav: Nav, expanded: Boolean, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val tint = if (focused || selected) TvColors.text else TvColors.textDim
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(14.dp), glowColor = TvColors.accent, modifier = modifier.fillMaxWidth().height(40.dp).onFocusChanged { focused = it.isFocused }) {
        Row(
            Modifier.fillMaxSize().then(if (selected && !focused) Modifier.background(Color(0x2AFFFFFF)) else Modifier).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(nav.icon, contentDescription = nav.label, tint = tint, modifier = Modifier.size(22.dp))
            if (expanded) Text(nav.label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Um slide do carrossel: conteúdo publicado por um app ou uma entrada HDMI. */
sealed class HeroSlide {
    data class Media(val item: MediaItem) : HeroSlide()
    data class Input(val input: InputEntry) : HeroSlide()
}

/** Carrossel: um slide por vez, ← → trocam, troca sozinho a cada 8 s sem foco. OK abre. */
@Composable
private fun Hero(items: List<HeroSlide>, appLabel: (String) -> String, onFocused: (MediaItem?) -> Unit, onOpen: (MediaItem) -> Unit, onOpenInput: (InputEntry) -> Unit, modifier: Modifier = Modifier) {
    var index by remember(items) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(items, focused) {
        if (items.size > 1 && !focused) while (true) { delay(8_000); index = (index + 1) % items.size }
    }
    val slide = items.getOrNull(index)
    val item = (slide as? HeroSlide.Media)?.item
    val input = (slide as? HeroSlide.Input)?.input
    val shape = RoundedCornerShape(18.dp)
    FocusSurface(
        onClick = { if (item != null) onOpen(item) else if (input != null) onOpenInput(input) }, shape = shape, focusScale = 1.02f,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused(item) }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || items.size < 2) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionRight -> { if (index < items.size - 1) { index++; true } else false }   // no último, o foco segue para os widgets
                    Key.DirectionLeft -> { if (index > 0) { index--; true } else false }
                    else -> false
                }
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item?.imageUri != null) {
                AsyncImage(model = item.imageUri, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xD90B0F14), 0.55f to Color(0x330B0F14), 1f to Color.Transparent)))
            }
            if (input != null) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B2838), Color(0xFF0B0F14)))))
                Icon(Icons.Rounded.SettingsInputHdmi, contentDescription = null, tint = Color(0x22FFFFFF), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 40.dp).size(120.dp))
            }
            Column(Modifier.align(Alignment.CenterStart).padding(start = 26.dp, end = 26.dp).fillMaxWidth(0.6f)) {
                if (input != null) {
                    Text("ENTRADAS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TvColors.textDim, letterSpacing = 1.sp)
                    Text(input.label, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Videogame, computador ou outro aparelho ligado nesta entrada.", fontSize = 13.sp, color = TvColors.textDim, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (focused) TvColors.text else Color(0x33FFFFFF)).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.SettingsInputHdmi, contentDescription = null, tint = if (focused) TvColors.background else TvColors.text, modifier = Modifier.size(18.dp))
                        Text("Abrir entrada", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (focused) TvColors.background else TvColors.text)
                    }
                } else if (item == null) {
                    Text("Minha TV", fontSize = 13.sp, color = TvColors.textDim, letterSpacing = 1.sp)
                    Text("Tudo em um lugar", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
                    Text("Os apps publicam aqui o que você está assistindo e as novidades.", fontSize = 13.sp, color = TvColors.textDim)
                } else {
                    Text(appLabel(item.packageName).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TvColors.textDim, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 34.sp)
                    val sub = item.episodeTitle ?: item.description
                    if (!sub.isNullOrBlank()) Text(sub, fontSize = 13.sp, color = TvColors.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (focused) TvColors.text else Color(0x33FFFFFF)).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = if (focused) TvColors.background else TvColors.text, modifier = Modifier.size(18.dp))
                        Text(if (item.isWatchNext) "Continuar" else "Assistir agora", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (focused) TvColors.background else TvColors.text)
                    }
                }
            }
            if (items.size > 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in items.indices) Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(if (i == index) TvColors.text else Color(0x66FFFFFF)))
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(weather: WeatherRepository.Weather?, enabled: Boolean, modifier: Modifier = Modifier, weatherFailed: Boolean = LocalFeedFailed.current) {
    Column(modifier.glass(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        if (weather != null && enabled) {
            // Agora: ícone e temperatura grandes; condição, mín/máx e sensação ao lado
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(weather.icon, fontSize = 30.sp)
                Text("${weather.tempC.roundToInt()}°", fontSize = 38.sp, fontWeight = FontWeight.Light, color = TvColors.text)
                Column {
                    Text(weather.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("mín ${weather.minC.roundToInt()}° · máx ${weather.maxC.roundToInt()}°", fontSize = 12.sp, color = TvColors.textDim, maxLines = 1)
                    weather.feelsC?.let { Text("sensação ${it.roundToInt()}°", fontSize = 12.sp, color = TvColors.textDim, maxLines = 1) }
                    if (weather.rainPct >= 20) Text("chuva ${weather.rainPct}%", fontSize = 12.sp, color = TvColors.textDim, maxLines = 1)
                }
            }
            // Próximos 3 dias
            if (weather.next.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for (d in weather.next) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(weekday(d.date), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TvColors.textDim)
                        Text(d.icon, fontSize = 18.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${d.maxC.roundToInt()}°", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TvColors.text)
                            Text(" ${d.minC.roundToInt()}°", fontSize = 12.sp, color = TvColors.textDim)
                        }
                    }
                }
            }
        } else {
            Text("Clima", fontSize = 13.sp, color = TvColors.textDim)
            Text(if (!enabled) "desligado" else if (weatherFailed) "sem conexão" else "carregando…", fontSize = 12.sp, color = TvColors.textDim)
        }
    }
}

/** "2026-10-29" → "Qui" (dia da semana abreviado, em português). */
private fun weekday(date: String): String = runCatching {
    java.time.LocalDate.parse(date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("pt", "BR"))
        .trimEnd('.').replaceFirstChar { it.uppercase() }
}.getOrDefault("")

@Composable
private fun InputsCard(hdmi: List<InputEntry>, onOpenInput: (InputEntry) -> Unit, modifier: Modifier = Modifier) {
    val first = hdmi.first()
    FocusSurface(onClick = { onOpenInput(first) }, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.SettingsInputHdmi, contentDescription = null, tint = TvColors.text, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text("Entrada", fontSize = 11.sp, color = TvColors.textDim, letterSpacing = 1.sp)
                Text(first.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hdmi.size > 1) Text("+${hdmi.size - 1} em Apps", fontSize = 11.sp, color = TvColors.textDim)
        }
    }
}

@Composable
private fun TunerCard(onOpenTuner: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(onClick = onOpenTuner, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = TvColors.text, modifier = Modifier.size(22.dp))
            Column {
                Text("TV aberta", fontSize = 11.sp, color = TvColors.textDim, letterSpacing = 1.sp)
                Text("Último canal", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TvColors.text)
            }
        }
    }
}

/**
 * Tela de categoria da barra lateral: linhas de conteúdo (Filmes e Séries) ou grade de apps (Jogos, Música).
 */
@Composable
fun CategoryScreen(
    title: String,
    rows: List<Pair<String, List<MediaItem>>>,
    apps: List<AppEntry>,
    appLabel: (String) -> String,
    onOpenItem: (MediaItem) -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onFocused: (MediaItem?) -> Unit,
) {
    val side = LocalDims.current.side
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)) {
        item { PageHeader(title, if (apps.isEmpty()) null else "seus apps e o que eles publicaram") }
        if (apps.isNotEmpty()) {
            item(key = "apps-title") { RowTitle(if (apps.size == 1) "1 app" else "${apps.size} apps") }
            item(key = "apps") {
                LazyRow(contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(apps, key = { it.packageName }) { AppIcon(it) { onOpenApp(it) } }
                }
            }
        }
        if (rows.all { it.second.isEmpty() }) {
            item {
                RowTitle(
                    if (apps.isEmpty()) "Nada por aqui ainda" else "Sem sugestões ainda",
                    if (apps.isEmpty()) "Nenhum app instalado nesta categoria." else "As sugestões vêm do que os apps publicam no Watch Next e nos canais de prévia.",
                )
            }
        }
        for ((name, list) in rows) {
            if (list.isEmpty()) continue
            item(key = "t-$name") { RowTitle(name) }
            item(key = "r-$name") { MediaRow(list, appLabel, onOpenItem, onFocused) }
        }
    }
}
