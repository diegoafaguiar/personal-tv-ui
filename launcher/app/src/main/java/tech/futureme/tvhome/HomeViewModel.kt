package tech.futureme.tvhome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.futureme.tvhome.data.AppsRepository
import tech.futureme.tvhome.data.Classifier
import tech.futureme.tvhome.data.HomeContent
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.data.Prefs
import tech.futureme.tvhome.data.Section
import tech.futureme.tvhome.data.Settings
import tech.futureme.tvhome.data.TvContentRepository

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val appsRepo = AppsRepository(app)
    private val tvRepo = TvContentRepository(app)

    private val _content = MutableStateFlow(HomeContent())
    val content: StateFlow<HomeContent> = _content
    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun refresh() {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val apps = appsRepo.apps()
                val inputs = appsRepo.inputs()
                val loaded = tvRepo.load()
                val grouped = loaded.items.groupBy { Classifier.section(it) }
                    .mapValues { (section, items) -> dedupe(if (section == Section.CONTINUE) items.sortedByDescending { it.lastEngagementMs } else items) }
                val perApp = loaded.items.filter { !it.isWatchNext }.groupBy { it.packageName }.mapValues { dedupe(it.value) }
                HomeContent(apps, inputs, grouped, perApp, tvRepo.hasPermission(), loaded.diagnostics)
            }
            _content.value = result
            _loading.value = false
        }
    }

    /** Netflix e companhia repetem o mesmo título em vários canais. */
    private fun dedupe(items: List<MediaItem>): List<MediaItem> {
        val seen = HashSet<String>()
        return items.filter { seen.add("${it.packageName}|${it.title.lowercase()}") }
    }
}
