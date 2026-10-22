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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.futureme.tvhome.data.AppsRepository
import tech.futureme.tvhome.data.Classifier
import tech.futureme.tvhome.data.FlamengoRepository
import tech.futureme.tvhome.data.TeamContent
import tech.futureme.tvhome.data.HomeContent
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.data.Prefs
import tech.futureme.tvhome.data.RatesRepository
import tech.futureme.tvhome.data.RecentEntry
import tech.futureme.tvhome.data.StatusRepository
import tech.futureme.tvhome.data.WeatherRepository
import tech.futureme.tvhome.data.Section
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

    private val _team = MutableStateFlow(TeamContent())
    val team: StateFlow<TeamContent> = _team

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
        // Cotações: a cada 30 min enquanto a home existir (sem serviço em background).
        viewModelScope.launch {
            while (true) { refreshRates(force = false); refreshWeather(force = false); refreshTeam(force = false); delay(30 * 60 * 1000L) }
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
                HomeContent(apps, inputs, grouped, perApp, broadcast, fast, sports, tvRepo.hasPermission(), loaded.diagnostics, statusRepo.read())
            }
            _content.value = result
            _loading.value = false
        }
        viewModelScope.launch { refreshRates(force = false); refreshWeather(force = false); refreshTeam(force = false) }
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
