package io.github.diegoafaguiar.sintonia

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.diegoafaguiar.sintonia.data.AppsRepository
import io.github.diegoafaguiar.sintonia.data.Classifier
import io.github.diegoafaguiar.sintonia.data.FlamengoRepository
import io.github.diegoafaguiar.sintonia.data.TeamContent
import io.github.diegoafaguiar.sintonia.data.HomeContent
import io.github.diegoafaguiar.sintonia.data.MediaItem
import io.github.diegoafaguiar.sintonia.data.Prefs
import io.github.diegoafaguiar.sintonia.data.Profile
import io.github.diegoafaguiar.sintonia.data.BgRotation
import io.github.diegoafaguiar.sintonia.data.Profiles
import io.github.diegoafaguiar.sintonia.data.RatesRepository
import io.github.diegoafaguiar.sintonia.data.RecentEntry
import io.github.diegoafaguiar.sintonia.data.StatusRepository
import io.github.diegoafaguiar.sintonia.data.WeatherRepository
import io.github.diegoafaguiar.sintonia.data.Section
import io.github.diegoafaguiar.sintonia.data.NewsRepository
import io.github.diegoafaguiar.sintonia.data.NewsContent
import io.github.diegoafaguiar.sintonia.data.NewsItem
import io.github.diegoafaguiar.sintonia.data.MusicRepository
import io.github.diegoafaguiar.sintonia.data.MusicContent
import io.github.diegoafaguiar.sintonia.data.Settings
import io.github.diegoafaguiar.sintonia.data.TvContentRepository

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val appsRepo = AppsRepository(app)
    private val tvRepo = TvContentRepository(app)
    private val ratesRepo = RatesRepository()
    private val weatherRepo = WeatherRepository()
    private val statusRepo = StatusRepository(app)
    private val teamRepo = FlamengoRepository()
    private val newsRepo = NewsRepository()
    private val musicRepo = MusicRepository()
    private val youtubeRepo = io.github.diegoafaguiar.sintonia.data.YoutubeRepository()
    private val usageRepo = io.github.diegoafaguiar.sintonia.data.UsageRepository(app)

    /** Página do câmbio: histórico de 30 dias por moeda e notícias. */
    data class RatesDetail(
        val history: Map<String, List<RatesRepository.Point>> = emptyMap(),
        val news: List<io.github.diegoafaguiar.sintonia.data.NewsItem> = emptyList(),
        val loading: Boolean = false,
        val fetchedAt: Long = 0L,
    )
    private val _ratesDetail = MutableStateFlow(RatesDetail())
    val ratesDetail: StateFlow<RatesDetail> = _ratesDetail

    /** Abre a página do câmbio: cotação na hora, histórico e notícias (cache de 10 min). */
    fun loadRatesDetail() {
        val cur = _ratesDetail.value
        if (cur.loading || System.currentTimeMillis() - cur.fetchedAt < 10 * 60 * 1000L) return
        _ratesDetail.value = cur.copy(loading = true)
        viewModelScope.launch {
            refreshRates(force = true)
            val r = withContext(Dispatchers.IO) {
                coroutineScope {
                    val usd = async { ratesRepo.history("USD") }
                    val eur = async { ratesRepo.history("EUR") }
                    val news = async { newsRepo.search("dólar OR euro cotação câmbio", topic = "Câmbio", max = 12) }
                    RatesDetail(mapOf("USD" to usd.await(), "EUR" to eur.await()), news.await(), false, System.currentTimeMillis())
                }
            }
            // Falhou tudo? Não guarda o horário, para tentar de novo na próxima abertura.
            _ratesDetail.value = if (r.history.values.all { it.isEmpty() } && r.news.isEmpty()) r.copy(fetchedAt = 0L) else r
        }
    }

    private val _news = MutableStateFlow(NewsContent())
    val news: StateFlow<NewsContent> = _news
    private val _music = MutableStateFlow(MusicContent())
    /** A última busca falhou (sem internet): a tela mostra "sem conexão" e "Tentar de novo". */
    val musicFailed = MutableStateFlow(false)
    val weatherFailed = MutableStateFlow(false)
    fun retryMusic() { musicFailed.value = false; viewModelScope.launch { refreshMusic(force = true) } }
    val music: StateFlow<MusicContent> = _music

    private val _team = MutableStateFlow(TeamContent())
    val team: StateFlow<TeamContent> = _team

    /** Perfil em uso. Nulo = mostrar o seletor (TV ligada agora, ou troca pedida pelo usuário). */
    val profile = MutableStateFlow<Profile?>(null)
    /** Verdadeiro enquanto ainda não sabemos se há perfil a restaurar (evita piscar o seletor). */
    val profileRestoring = MutableStateFlow(true)

    /** Incrementa quando o botão Home é pressionado com o launcher já aberto: a UI volta ao Início. */
    val homePressed = MutableStateFlow(0)
    /** Controle pelo celular: busca pedida (abre a tela de busca com o texto) e TV ao vivo. */
    val remoteSearch = MutableStateFlow<Pair<String, Int>?>(null)   // (texto, contador): repetir a mesma busca reabre
    val remoteLiveTv = MutableStateFlow(0)

    fun selectProfile(p: Profile?) {
        profile.value = p
        viewModelScope.launch { prefs.setActiveProfile(p?.id) }
    }

    private val _content = MutableStateFlow(HomeContent())
    val content: StateFlow<HomeContent> = _content
    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _rates = MutableStateFlow<List<RatesRepository.Rate>>(emptyList())
    val rates: StateFlow<List<RatesRepository.Rate>> = _rates
    private var ratesFetchedAt = 0L

    private val _weather = MutableStateFlow<WeatherRepository.Weather?>(null)
    val weather: StateFlow<WeatherRepository.Weather?> = _weather
    private var weatherFetchedAt = 0L

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    init {
        // Processo morto pelo sistema no mesmo boot (ex.: Netflix aberto numa TV de 2 GB): volta ao perfil sem o seletor.
        viewModelScope.launch { try { prefs.migrateWallpaper() } catch (e: Exception) { /* fica como está */ } }
        viewModelScope.launch {
            try { prefs.activeProfileForThisBoot()?.let { id -> Profiles.byId(id)?.let { profile.value = it } } } catch (e: Exception) { /* seletor */ }
            profileRestoring.value = false
        }
        // TV foi para o standby: ao ligar, o seletor de perfil (ver TvHomeApp). drop(1): só o que
        // acontecer depois daqui (uma Activity recriada não perde o perfil escolhido)
        viewModelScope.launch { TvHomeApp.screenOffs.drop(1).collect { profile.value = null } }
        // Fundo rotativo: por tempo, ou a cada vez que a TV liga
        viewModelScope.launch {
            prefs.settings.map { it.bgRotation }.distinctUntilChanged().collectLatest { r ->
                if (r.minutes > 0) while (true) { delay(r.minutes * 60_000L); prefs.nextBackground() }
                else if (r == BgRotation.AO_LIGAR) TvHomeApp.screenOns.drop(1).collect { prefs.nextBackground() }
            }
        }
        // Cotações: a cada 30 min enquanto a home existir (sem serviço em background).
        viewModelScope.launch {
            while (true) { refreshRates(force = false); refreshWeather(force = false); refreshTeam(force = false); refreshNews(force = false); refreshMusic(force = false); delay(30 * 60 * 1000L) }
        }
    }

    fun refresh() {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val apps = appsRepo.apps()
                val inputs = appsRepo.inputs()
                val loaded = tvRepo.load()
                val s = settings.value
                val opens = s.recents.groupingBy { it.packageName }.eachCount()
                // Ordem dos apps em todas as seções: a lista de preferência; empate pelos mais abertos.
                val byApp = compareBy<String> { s.appRank(it) }.thenByDescending { opens[it] ?: 0 }

                // Continuar assistindo: por app na ordem de preferência, no máximo 5 por app, mais recentes primeiro.
                val watchNext = dedupe(loaded.items.filter { it.isWatchNext }.sortedByDescending { it.lastEngagementMs })
                val continueRow = watchNext.groupBy { it.packageName }.entries
                    .sortedWith(compareBy(byApp) { it.key })
                    .flatMap { it.value.take(5) }

                // Seções temáticas: tudo que os apps publicam, na mesma ordem de apps.
                val thematic = dedupe(loaded.items).groupBy { Classifier.section(it) }
                    .mapValues { (_, items) -> items.sortedWith(compareBy(byApp) { it.packageName }) }
                val grouped = mapOf(Section.CONTINUE to continueRow) + thematic.filterKeys { it != Section.CONTINUE }
                val perApp = loaded.items.filter { !it.isWatchNext }.groupBy { it.packageName }.mapValues { dedupe(it.value) }
                    .toSortedMap(byApp)

                // Em alta: o que cada app destaca (canais de prévia, com imagem), sem o que já está em
                // Continuar assistindo; os apps mais usados nos últimos 30 dias primeiro, até 4 itens de cada.
                val usage = usageRepo.foregroundTime()
                val inContinue = continueRow.map { it.packageName + it.title }.toSet()
                val hot = perApp.entries
                    .sortedWith(compareByDescending<Map.Entry<String, List<MediaItem>>> { usage[it.key] ?: 0L }.thenBy(byApp) { it.key })
                    .flatMap { (_, items) -> items.filter { it.imageUri != null && it.packageName + it.title !in inContinue }.take(4) }
                    .take(24)

                val broadcast = loaded.tuner.filter { it.isBroadcast }
                val fast = loaded.tuner.filter { !it.isBroadcast }
                val sports = (broadcast + fast).filter { Classifier.isSportsChannel(it) }
                val globo = broadcast.firstOrNull { it.name.contains("globo", true) }
                val schedule = try { globo?.let { tvRepo.programs(it, 10) } ?: emptyList() } catch (e: Exception) { emptyList() }
                HomeContent(apps, inputs, grouped, perApp, broadcast, fast, sports, tvRepo.hasPermission(), loaded.diagnostics, statusRepo.read(), globo, schedule, hot, usage.isNotEmpty())
            }
            _content.value = result
            _loading.value = false
        }
        viewModelScope.launch { refreshRates(force = false); refreshWeather(force = false); refreshTeam(force = false); refreshNews(force = false); refreshMusic(force = false) }
    }

    /**
     * TV ligada (tela acendeu) ou launcher aberto pela primeira vez: recomendações, notícias,
     * lançamentos, paradas, cotações e clima são buscados de novo, sem esperar o intervalo.
     */
    fun refreshFeedsNow() {
        viewModelScope.launch {
            refreshNews(force = true); refreshMusic(force = true); refreshTeam(force = true)
            refreshRates(force = true); refreshWeather(force = true)
        }
        refresh()
    }

    /** Notícias do time e de tecnologia (ligadas aos apps instalados), a cada hora. */
    private suspend fun refreshNews(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - _news.value.fetchedAt < 60 * 60 * 1000L) return
        val s = settings.value
        val apps = _content.value.apps
        // Apps "de tecnologia/streaming" que valem notícia: os do dock primeiro, até 6 nomes
        val names = (s.dock.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg }?.label } + apps.map { it.label })
            .distinct().filter { it.length in 3..20 }.take(6)
        val r = withContext(Dispatchers.IO) {
            // As três buscas em paralelo: a primeira carga da home fica ~3x mais rápida
            val teamD = async { if (s.showTeam) newsRepo.search(s.teamName, max = 12) else emptyList() }
            val techD = async { newsRepo.search("tecnologia", max = 8) }
            val appsD = async { if (names.isEmpty()) emptyList() else newsRepo.search("(" + names.joinToString(" OR ") { "\"$it\"" } + ") (app OR aplicativo OR recurso OR lançamento OR atualização OR streaming OR preço)", topic = "Apps", max = 8) }
            // Lançamentos: estreias da semana nos streamings instalados (Netflix, Prime, Disney+, Globoplay…)
            val streamNames = names.filter { n -> listOf("netflix", "prime", "disney", "globoplay", "max", "apple tv", "paramount", "claro").any { n.lowercase().contains(it) } }
            val relD = async {
                val q = if (streamNames.isEmpty()) "estreias streaming semana" else "(estreias OR lançamentos OR \"chega ao\") (" + streamNames.joinToString(" OR ") { "\"$it\"" } + ")"
                newsRepo.search(q, topic = "Lançamentos", max = 12)
            }
            val team = teamD.await(); val tech = techD.await(); val appsNews = appsD.await(); val releases = relD.await()
            // Intercala: uma de tecnologia, uma dos apps
            val mixed = ArrayList<NewsItem>()
            val a = tech.iterator(); val b = appsNews.iterator()
            while (a.hasNext() || b.hasNext()) { if (a.hasNext()) mixed += a.next(); if (b.hasNext()) mixed += b.next() }
            NewsContent(team, mixed.distinctBy { it.title }, releases.distinctBy { it.title }, now)
        }
        _news.value = r
    }

    /** Paradas musicais (Deezer), a cada 6 h. */
    private suspend fun refreshMusic(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - _music.value.fetchedAt < 6 * 60 * 60 * 1000L) return
        val r = withContext(Dispatchers.IO) {
            val chart = musicRepo.chart()
            // Clipe de cada uma das 8 mais tocadas + vídeos musicais em alta, em paralelo
            val clipsD = chart.tracks.take(8).map { t -> async { youtubeRepo.search("${t.query} clipe oficial", max = 1).firstOrNull() } }
            val ytD = async { youtubeRepo.search("músicas mais tocadas ao vivo", max = 12, recent = false) }
            val clips = clipsD.mapNotNull { it.await() }.distinctBy { it.id }.map { youtubeRepo.toItem(it) }
            chart.copy(clips = clips, youtube = ytD.await().map { youtubeRepo.toItem(it) })
        }
        if (r.fetchedAt > 0 || r.clips.isNotEmpty()) { _music.value = r; musicFailed.value = false } else musicFailed.value = true
    }

    fun recordRecent(packageName: String, title: String, intentUri: String?) {
        viewModelScope.launch { prefs.recordRecent(RecentEntry(packageName, title, intentUri, System.currentTimeMillis())) }
    }

    private suspend fun refreshTeam(force: Boolean) {
        val s = settings.value
        if (!s.showTeam) return
        if (!force && System.currentTimeMillis() - _team.value.fetchedAt < 25 * 60 * 1000L) return
        val r0 = withContext(Dispatchers.IO) { teamRepo.fetch(s.teamEspnId, s.teamLeague, s.teamYoutube) }
        // RSS de vídeos do YouTube morreu (404): busca os vídeos mais recentes do time
        val r = if (r0.videos.isNotEmpty()) r0 else r0.copy(videos = withContext(Dispatchers.IO) {
            youtubeRepo.search("${s.teamName} melhores momentos", max = 12, recent = true).map { youtubeRepo.toItem(it, "SPORTS") }
        })
        val name = s.teamName.lowercase()
        val related = _content.value.allItems.filter { (it.title + " " + (it.description ?: "")).lowercase().contains(name) }
        _team.value = TeamContent(r.info, r.videos.take(20), related, r.errors, System.currentTimeMillis())
    }

    private suspend fun refreshWeather(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - weatherFetchedAt < 25 * 60 * 1000L) return
        val s = settings.value
        val w = withContext(Dispatchers.IO) { weatherRepo.fetch(s.latitude, s.longitude) }
        if (w != null) { _weather.value = w; weatherFetchedAt = now; weatherFailed.value = false } else weatherFailed.value = true
    }

    private suspend fun refreshRates(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - ratesFetchedAt < 25 * 60 * 1000L) return
        val list = withContext(Dispatchers.IO) { ratesRepo.fetch() }
        if (list.isNotEmpty()) { _rates.value = list; ratesFetchedAt = now }
    }

    /** Netflix e companhia repetem o mesmo título em vários canais. */
    private fun dedupe(items: List<MediaItem>): List<MediaItem> {
        val seen = HashSet<String>()
        return items.filter { seen.add("${it.packageName}|${it.title.lowercase()}") }
    }
}
