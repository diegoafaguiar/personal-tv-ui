package tech.futureme.tvhome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.futureme.tvhome.data.AppsRepository
import tech.futureme.tvhome.data.Classifier
import tech.futureme.tvhome.data.FlamengoRepository
import tech.futureme.tvhome.data.TeamContent
import tech.futureme.tvhome.data.HomeContent
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.data.Prefs
import tech.futureme.tvhome.data.Profile
import tech.futureme.tvhome.data.Profiles
import tech.futureme.tvhome.data.RatesRepository
import tech.futureme.tvhome.data.RecentEntry
import tech.futureme.tvhome.data.StatusRepository
import tech.futureme.tvhome.data.WeatherRepository
import tech.futureme.tvhome.data.Section
import tech.futureme.tvhome.data.NewsRepository
import tech.futureme.tvhome.data.NewsContent
import tech.futureme.tvhome.data.NewsItem
import tech.futureme.tvhome.data.MusicRepository
import tech.futureme.tvhome.data.MusicContent
import tech.futureme.tvhome.data.Settings
import tech.futureme.tvhome.data.TvContentRepository

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

    private val _news = MutableStateFlow(NewsContent())
    val news: StateFlow<NewsContent> = _news
    private val _music = MutableStateFlow(MusicContent())
    val music: StateFlow<MusicContent> = _music

    private val _team = MutableStateFlow(TeamContent())
    val team: StateFlow<TeamContent> = _team

    /** Perfil em uso. Nulo = mostrar o seletor (TV ligada agora, ou troca pedida pelo usuário). */
    val profile = MutableStateFlow<Profile?>(null)
    /** Verdadeiro enquanto ainda não sabemos se há perfil a restaurar (evita piscar o seletor). */
    val profileRestoring = MutableStateFlow(true)

    /** Incrementa quando o botão Home é pressionado com o launcher já aberto: a UI volta ao Início. */
    val homePressed = MutableStateFlow(0)

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

                val broadcast = loaded.tuner.filter { it.isBroadcast }
                val fast = loaded.tuner.filter { !it.isBroadcast }
                val sports = (broadcast + fast).filter { Classifier.isSportsChannel(it) }
                val globo = broadcast.firstOrNull { it.name.contains("globo", true) }
                val schedule = try { globo?.let { tvRepo.programs(it, 10) } ?: emptyList() } catch (e: Exception) { emptyList() }
                HomeContent(apps, inputs, grouped, perApp, broadcast, fast, sports, tvRepo.hasPermission(), loaded.diagnostics, statusRepo.read(), globo, schedule)
            }
            _content.value = result
            _loading.value = false
        }
        viewModelScope.launch { refreshRates(force = false); refreshWeather(force = false); refreshTeam(force = false); refreshNews(force = false); refreshMusic(force = false) }
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
            val team = teamD.await(); val tech = techD.await(); val appsNews = appsD.await()
            // Intercala: uma de tecnologia, uma dos apps
            val mixed = ArrayList<NewsItem>()
            val a = tech.iterator(); val b = appsNews.iterator()
            while (a.hasNext() || b.hasNext()) { if (a.hasNext()) mixed += a.next(); if (b.hasNext()) mixed += b.next() }
            NewsContent(team, mixed.distinctBy { it.title }, now)
        }
        _news.value = r
    }

    /** Paradas musicais (Deezer), a cada 6 h. */
    private suspend fun refreshMusic(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - _music.value.fetchedAt < 6 * 60 * 60 * 1000L) return
        val r = withContext(Dispatchers.IO) { musicRepo.chart() }
        if (r.fetchedAt > 0) _music.value = r
    }

    fun recordRecent(packageName: String, title: String, intentUri: String?) {
        viewModelScope.launch { prefs.recordRecent(RecentEntry(packageName, title, intentUri, System.currentTimeMillis())) }
    }

    private suspend fun refreshTeam(force: Boolean) {
        val s = settings.value
        if (!s.showTeam) return
        if (!force && System.currentTimeMillis() - _team.value.fetchedAt < 25 * 60 * 1000L) return
        val r = withContext(Dispatchers.IO) { teamRepo.fetch(s.teamEspnId, s.teamLeague, s.teamYoutube) }
        val name = s.teamName.lowercase()
        val related = _content.value.allItems.filter { (it.title + " " + (it.description ?: "")).lowercase().contains(name) }
        _team.value = TeamContent(r.info, r.videos.take(20), related, r.errors, System.currentTimeMillis())
    }

    private suspend fun refreshWeather(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - weatherFetchedAt < 25 * 60 * 1000L) return
        val s = settings.value
        val w = withContext(Dispatchers.IO) { weatherRepo.fetch(s.latitude, s.longitude) }
        if (w != null) { _weather.value = w; weatherFetchedAt = now }
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
