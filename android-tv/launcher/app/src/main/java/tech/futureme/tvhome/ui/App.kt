@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package tech.futureme.tvhome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tech.futureme.tvhome.HomeViewModel
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.InputEntry
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.data.NewsItem
import tech.futureme.tvhome.data.Profile
import tech.futureme.tvhome.data.Profiles
import tech.futureme.tvhome.data.QuickAction
import tech.futureme.tvhome.data.RecentEntry
import tech.futureme.tvhome.data.TunerChannel
import java.io.File
import java.util.Calendar

private enum class Screen { HOME, ALL_APPS, SETTINGS, DIAGNOSTICS, SEARCH, LIVE_TV, PIN_CHANGE, CATEGORY, READER, EXPLORE, RATES, REMOTE }

@Composable
fun App(
    vm: HomeViewModel,
    onOpenItem: (MediaItem) -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onOpenInput: (InputEntry) -> Unit,
    onOpenRecent: (RecentEntry) -> Unit,
    onTuneFailed: (TunerChannel, String) -> Unit,
    onQuick: (QuickAction) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenUrl: (String) -> Boolean = { false },
    onOpenMusic: (String) -> Unit = {},
    onOpenYoutube: (String) -> Unit = {},
) {
    val content by vm.content.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rates by vm.rates.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val team by vm.team.collectAsStateWithLifecycle()
    val activeProfile by vm.profile.collectAsStateWithLifecycle()
    val restoring by vm.profileRestoring.collectAsStateWithLifecycle()
    val homeTick by vm.homePressed.collectAsStateWithLifecycle()
    val remoteQuery by vm.remoteSearch.collectAsStateWithLifecycle()
    val remoteLive by vm.remoteLiveTv.collectAsStateWithLifecycle()
    var pendingProfile by remember { mutableStateOf<Profile?>(null) }   // escolhido, aguardando senha
    var pinChangeFor by remember { mutableStateOf<Profile?>(null) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    val news by vm.news.collectAsStateWithLifecycle()
    val music by vm.music.collectAsStateWithLifecycle()
    var reading by remember { mutableStateOf<NewsItem?>(null) }
    // De onde a leitura foi aberta: Voltar retorna para lá (ex.: página do câmbio)
    var readerFrom by remember { mutableStateOf(Screen.HOME) }
    // Notícia: navegador/app se houver; senão a tela de leitura
    fun openNews(item: NewsItem) { if (!onOpenUrl(item.link)) { reading = item; readerFrom = screen; screen = Screen.READER } }
    val ratesDetail by vm.ratesDetail.collectAsStateWithLifecycle()
    fun openRates() { vm.loadRatesDetail(); screen = Screen.RATES }
    fun openYoutubeSearch(q: String) { onOpenYoutube(q) }
    var focused by remember { mutableStateOf<MediaItem?>(null) }
    var liveChannel by remember { mutableStateOf<TunerChannel?>(null) }
    var category by remember { mutableStateOf(Nav.FILMES) }
    val contentFocus = remember { FocusRequester() }
    var exploreCat by remember { mutableStateOf(Category.DESTAQUES) }
    // Home pressionado: qualquer tela volta ao Início (a TV ao vivo e a senha também fecham)
    LaunchedEffect(homeTick) { if (homeTick > 0) { screen = Screen.HOME; pendingProfile = null; reading = null } }
    val allChannels = content.tunerChannels + content.fastChannels
    fun openChannel(ch: TunerChannel) { liveChannel = ch; screen = Screen.LIVE_TV }
    val scope = rememberCoroutineScope()
    val appLabel: (String) -> String = { pkg -> content.apps.firstOrNull { it.packageName == pkg }?.label ?: vm.appsRepo.label(pkg) }

    // Voltar: fecha a tela atual; na home não faz nada (como HOME, sair da Activity
    // faria o sistema reabri-la e o seletor de perfil voltar).
    BackHandler(enabled = true) {
        if (pendingProfile != null) pendingProfile = null
        else if (screen == Screen.PIN_CHANGE || screen == Screen.DIAGNOSTICS || screen == Screen.REMOTE) { pinChangeFor = null; screen = Screen.SETTINGS }
        else if (screen == Screen.READER) { reading = null; screen = readerFrom }
        else if (screen != Screen.HOME) screen = Screen.HOME
    }

    val profile = activeProfile
    val glass = remember { GlassState() }
    val context = LocalContext.current
    glass.config = settings.glass
    glass.background = settings.background
    // Papel de parede renderizado uma vez por (fundo, tamanho, desfoque); ver Glass.kt.
    LaunchedEffect(settings.background, glass.screen, settings.glass.blur, settings.wallpaperRev) {
        val sz = glass.screen
        if (sz == IntSize.Zero) return@LaunchedEffect
        val file = File(context.getExternalFilesDir(null), "wallpaper.jpg")
        try {
            val t0 = System.currentTimeMillis()
            val wp = renderWallpaper(context, settings.background, file, maxOf(1, sz.width / 2), maxOf(1, sz.height / 2), settings.glass.blur)
            glass.wallpaper = wp.sharp; glass.wallpaperBlur = wp.blur
            android.util.Log.i("TvHome", "Papel de parede ${settings.background} ${sz.width / 2}x${sz.height / 2} em ${System.currentTimeMillis() - t0} ms")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // trocou de fundo/tamanho no meio: o efeito reinicia, não é erro
        } catch (e: Throwable) {
            android.util.Log.w("TvHome", "Falha renderizando o papel de parede ${settings.background}", e)
        }
    }
    CompositionLocalProvider(
        LocalDims provides Dims.of(settings.density).scaled(settings.look.cardScale, settings.look.cardSpacing),
        LocalGlass provides glass,
        LocalLook provides settings.look,
        LocalBringIntoViewSpec provides MinimalScroll,
        LocalFeedFailed provides vm.weatherFailed.collectAsStateWithLifecycle().value,
    ) {
        Box(Modifier.fillMaxSize().onSizeChanged { if (it != glass.screen) glass.screen = it }) {
            if (screen != Screen.LIVE_TV) BackgroundLayer(if (screen == Screen.HOME && profile != null && !settings.glass.lite) focused?.imageUri else null, settings.nightMode)   // modo leve: sem a arte em tela cheia (2 camadas a menos por quadro)
            // Sem perfil: seletor (e senha, se o perfil pedir)
            if (profile == null) {
                if (restoring) return@Box   // só o fundo, até sabermos se há perfil a restaurar
                val pend = pendingProfile
                if (pend != null && pend.requiresPin) {
                    PinPad(
                        title = "Senha de ${pend.name}", subtitle = "Digite os 4 números",
                        expected = settings.pinFor(pend.id),
                        onDone = { vm.selectProfile(pend); pendingProfile = null; screen = Screen.HOME },
                        onCancel = { pendingProfile = null },
                    )
                } else {
                    ProfilePicker(Profiles.ALL) { p ->
                        if (p.requiresPin) pendingProfile = p else { vm.selectProfile(p); screen = Screen.HOME }
                    }
                }
                return@Box
            }
            val visibleApps = content.apps.filter { it.packageName !in settings.hidden && profile.allows(it.packageName) }
            // Botão "TV ao Vivo": abre o app de TV da própria TV já na antena (passthrough do sintonizador).
            // O TvView dentro do launcher fica para os cards de canal; na TCL ele recusa a sessão e caía
            // no app de TV no último input usado (ex.: HDMI 2).
            fun openTuner() {
                val tunerInput = vm.appsRepo.builtInTuner(content.inputs)
                if (tunerInput != null) { onOpenInput(tunerInput); return }
                val last = content.tunerChannels.firstOrNull { it.id == settings.lastChannelId }
                val ch = last ?: content.tunerChannels.firstOrNull { it.name.contains("globo", true) } ?: content.tunerChannels.firstOrNull()
                if (ch != null) openChannel(ch)
            }
            // Conteúdo de todos os apps (e vídeos do YouTube) agrupado por categoria, para o Explorar
            val categories = remember(content, team, music, profile) {
                groupByCategory((content.allItems + team.videos + music.clips + music.youtube).filter { profile.allows(it.packageName) }) { settings.appRank(it) }
            }
            // Controle pelo celular: busca com o texto digitado lá e TV ao vivo
            LaunchedEffect(remoteQuery) { if (remoteQuery != null) screen = Screen.SEARCH }
            LaunchedEffect(remoteLive) { if (remoteLive > 0) openTuner() }
            fun navigate(nav: Nav) {
                when (nav) {
                    Nav.INICIO -> screen = Screen.HOME
                    Nav.APPS -> screen = Screen.ALL_APPS
                    Nav.FILMES -> screen = Screen.EXPLORE
                    Nav.TV -> openTuner()
                    Nav.CONFIG -> screen = Screen.SETTINGS
                    else -> { category = nav; screen = Screen.CATEGORY }
                }
            }
            // Qual item da lateral corresponde à tela atual (marcado)
            val selectedNav = when (screen) {
                Screen.HOME -> Nav.INICIO; Screen.ALL_APPS -> Nav.APPS; Screen.SETTINGS -> Nav.CONFIG; Screen.EXPLORE -> Nav.FILMES
                Screen.CATEGORY -> category; else -> null
            }
            // Lateral persistente em todas as telas (menos home, TV ao vivo, seletor/senha): navegar sem Voltar
            val withSidebar = settings.glass.enabled && screen != Screen.HOME && screen != Screen.LIVE_TV && screen != Screen.PIN_CHANGE
            // Ao abrir uma seção (pela lateral ou pela home), o foco entra no conteúdo dela
            LaunchedEffect(screen, category, withSidebar) {
                // Explorar põe o foco na categoria escolhida sozinho (senão a primeira pílula a trocaria)
                if (withSidebar && screen != Screen.EXPLORE) { delay(250); runCatching { contentFocus.requestFocus() } }
            }
            @Composable fun Shell(content: @Composable () -> Unit) {
                if (!withSidebar) content() else Row(Modifier.fillMaxSize()) {
                    Sidebar(kids = profile.isKids, selected = selectedNav, onNav = ::navigate, rightTarget = contentFocus)
                    CompositionLocalProvider(LocalRowStart provides 16.dp) {
                        Box(Modifier.fillMaxSize().focusRequester(contentFocus).focusGroup()) { content() }
                    }
                }
            }
            Shell { when (screen) {
                Screen.HOME -> if (settings.glass.enabled) GlassHomeScreen(
                    content = content, settings = settings, profile = profile, weather = weather, rates = rates, team = team,
                    news = news, onOpenNews = ::openNews, onOpenYoutubeSearch = ::openYoutubeSearch,
                    appLabel = appLabel, onFocused = { focused = it },
                    onOpenItem = onOpenItem, onOpenApp = onOpenApp, onOpenInput = onOpenInput, onOpenRecent = onOpenRecent,
                    onOpenChannel = ::openChannel, onOpenTuner = ::openTuner, onQuick = onQuick,
                    onNav = ::navigate,
                    resetKey = homeTick,
                    categories = categories,
                    onOpenCategory = { c -> exploreCat = c; screen = Screen.EXPLORE },
                    onOpenRates = ::openRates,
                    onOpenSearch = { screen = Screen.SEARCH },
                    onSwitchProfile = { vm.selectProfile(null); focused = null },
                    onRequestPermission = onRequestPermission,
                ) else HomeScreen(
                    content = content, settings = settings, profile = profile, rates = rates, weather = weather, team = team,
                    focused = focused, onFocused = { focused = it }, appLabel = appLabel,
                    onOpenItem = onOpenItem, onOpenApp = onOpenApp, onOpenInput = onOpenInput,
                    onOpenRecent = onOpenRecent, onOpenChannel = ::openChannel,
                    onOpenTuner = ::openTuner,
                    onQuick = onQuick,
                    onOpenSearch = { screen = Screen.SEARCH },
                    onOpenAllApps = { screen = Screen.ALL_APPS },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                    onSwitchProfile = { vm.selectProfile(null); focused = null },
                    onRequestPermission = onRequestPermission,
                )
                Screen.ALL_APPS -> {
                    val all = content.apps.filter { it.packageName !in settings.hidden && profile.allows(it.packageName) }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(LocalDims.current.appW + 16.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = LocalRowStart.current ?: LocalDims.current.side, end = LocalDims.current.side, top = 18.dp, bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap),
                        verticalArrangement = Arrangement.spacedBy(LocalDims.current.gap),
                    ) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            PageHeader("Apps", "${all.size} instalados · ocultar e reordenar em Ajustes › Apps", inset = false)
                        }
                        items(all, key = { it.packageName }) { app -> AppCard(app, glassy = true) { onOpenApp(app) } }
                    }
                }
                Screen.SETTINGS -> SettingsScreen(
                    vm = vm, apps = content.apps, settings = settings,
                    onChangePin = { p -> pinChangeFor = p; screen = Screen.PIN_CHANGE },
                    onOpenTvSettings = { onQuick(QuickAction.CONFIG) },
                    onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                    onOpenRemote = { screen = Screen.REMOTE },
                )
                Screen.PIN_CHANGE -> {
                    val p = pinChangeFor
                    if (p == null) screen = Screen.SETTINGS
                    else PinPad(
                        title = "Nova senha de ${p.name}", subtitle = "Digite 4 números",
                        expected = null,
                        onDone = { pin -> scope.launch { vm.prefs.setPin(p.id, pin) }; pinChangeFor = null; screen = Screen.SETTINGS },
                        onCancel = { pinChangeFor = null; screen = Screen.SETTINGS },
                    )
                }
                Screen.CATEGORY -> {
                    fun List<MediaItem>.allowed() = filter { profile.allows(it.packageName) }
                    // Jogos/Música: continuar + sugestões publicadas pelos apps da categoria + os apps
                    fun rowsFor(apps: List<AppEntry>): List<Pair<String, List<MediaItem>>> {
                        val pkgs = apps.map { it.packageName }.toSet()
                        return listOf(
                            "Continuar" to content.sections[tech.futureme.tvhome.data.Section.CONTINUE].orEmpty().filter { it.packageName in pkgs },
                            "Sugestões" to content.perApp.filterKeys { it in pkgs }.values.flatten().take(30),
                        )
                    }
                    when (category) {
                        Nav.JOGOS -> {
                            val a = visibleApps.filter { it.isGame() }
                            if (a.isEmpty()) Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
                                PageHeader("Jogos")
                                EmptyState(
                                    "Nenhum jogo instalado",
                                    "Os jogos que você instalar aparecem aqui, com o que eles publicarem para continuar jogando.",
                                    "Procurar jogos na Play Store",
                                ) { onOpenUrl("market://search?q=jogos&c=apps") }
                            } else CategoryScreen("Jogos", rowsFor(a), a, appLabel, onOpenItem, onOpenApp) { focused = it }
                        }
                        Nav.MUSICA -> {
                            val a = visibleApps.filter { it.isMusic() }
                            val pkgs = a.map { it.packageName }.toSet()
                            MusicScreen(
                                apps = a, music = music,
                                continueItems = (content.sections[tech.futureme.tvhome.data.Section.CONTINUE].orEmpty() + content.perApp.filterKeys { it in pkgs }.values.flatten()).filter { it.packageName in pkgs }.distinctBy { it.title }.take(20),
                                recents = settings.recents.filter { it.packageName in pkgs },
                                appLabel = appLabel, onOpenApp = onOpenApp, onOpenItem = onOpenItem, onOpenRecent = onOpenRecent,
                                onOpenMusic = { onOpenMusic(it.query) }, onFocused = { focused = it },
                                failed = vm.musicFailed.collectAsStateWithLifecycle().value, onRetry = { vm.retryMusic() },
                            )
                        }
                        else -> CategoryScreen(
                            "Filmes e Séries",
                            listOf(
                                "Continuar assistindo" to content.sections[tech.futureme.tvhome.data.Section.CONTINUE].orEmpty().allowed(),
                                "Filmes" to content.sections[tech.futureme.tvhome.data.Section.FILMES].orEmpty().allowed(),
                                "Séries" to content.sections[tech.futureme.tvhome.data.Section.SERIES].orEmpty().allowed(),
                                "Para você" to content.sections[tech.futureme.tvhome.data.Section.OUTROS].orEmpty().allowed(),
                            ),
                            emptyList(), appLabel, onOpenItem, onOpenApp,
                        ) { focused = it }
                    }
                }
                Screen.DIAGNOSTICS -> DiagnosticsScreen(content, team, appLabel)
                Screen.LIVE_TV -> {
                    val ch = liveChannel
                    if (ch == null) screen = Screen.HOME
                    else LiveTvScreen(
                        channels = if (ch.isBroadcast) content.tunerChannels else allChannels,
                        initial = ch,
                        onFailed = { c, why -> screen = Screen.HOME; onTuneFailed(c, why) },
                        onChannelChanged = { c -> if (c.isBroadcast) scope.launch { vm.prefs.setLastChannel(c.id) } },
                    )
                }
                Screen.SEARCH -> SearchScreen(
                    items = (content.allItems + team.videos + music.clips + music.youtube).filter { profile.allows(it.packageName) }, apps = content.apps.filter { it.packageName !in settings.hidden && profile.allows(it.packageName) },
                    appLabel = appLabel, onOpenItem = onOpenItem, onOpenApp = onOpenApp, onSearchYoutube = onOpenYoutube,
                    initialQuery = remoteQuery?.first ?: "",
                )
                Screen.REMOTE -> RemoteScreen(vm.prefs, settings.remoteEnabled)
                Screen.EXPLORE -> ExploreScreen(
                    groups = categories, initial = exploreCat, appLabel = appLabel, rank = { settings.appRank(it) },
                    onOpenItem = onOpenItem, onFocused = { focused = it }, onCategoryChanged = { exploreCat = it },
                )
                Screen.READER -> { val r = reading; if (r == null) screen = Screen.HOME else ReaderScreen(r) }
                Screen.RATES -> RatesScreen(rates, ratesDetail, ::openNews)
            } }
        }
    }
}

/**
 * Fundo da home: o papel de parede (GlassBackground, ver Glass.kt). Com um item focado, a arte
 * dele aparece por cima, escurecida. À noite, escurece mais (e os painéis de vidro acompanham).
 */
@Composable
private fun BackgroundLayer(focusedImage: String?, nightMode: Boolean) {
    val glass = LocalGlass.current
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val night = nightMode && (hour >= 21 || hour < 6)
    glass.darken = if (night) 0.33f else 0f
    GlassBackground()
    if (focusedImage != null) {
        AsyncImage(model = focusedImage, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000), 1f to Color(0xEE0B0F14))))
    }
}
