package tech.futureme.tvhome.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tvhome")

/** Fundos predefinidos. "imagem" usa o arquivo enviado por scripts/58-launcher-wallpaper.sh. */
enum class Background(val label: String) {
    PRETO("Preto"), GRAFITE("Grafite"), NOITE("Azul-noite"), GRADIENTE("Gradiente"), IMAGEM("Minha imagem");
}

data class Settings(
    /** Pacotes do dock, na ordem. Vazio = os 8 primeiros apps instalados. */
    val dock: List<String> = DEFAULT_DOCK,
    val hidden: Set<String> = emptySet(),
    val liveTvPackage: String = "com.tcl.tv",
    val disabledSections: Set<String> = emptySet(),
    val background: Background = Background.NOITE,
    val density: String = "compacta",
    val quick: Set<QuickAction> = QuickAction.DEFAULT,
    val showRates: Boolean = true,
    val showInputs: Boolean = true,
) {
    companion object {
        val DEFAULT_DOCK = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.apple.atve.androidtv.appletv",
            "com.disney.disneyplus",
            "com.wbd.stream",
            "com.globo.globotv",
            "com.spotify.tv.android",
        )
    }
}

class Prefs(private val context: Context) {
    private val dockKey = stringPreferencesKey("dock")
    private val hiddenKey = stringSetPreferencesKey("hidden")
    private val liveTvKey = stringPreferencesKey("live_tv")
    private val sectionsOffKey = stringSetPreferencesKey("sections_off")
    private val backgroundKey = stringPreferencesKey("background")
    private val densityKey = stringPreferencesKey("density")
    private val quickKey = stringSetPreferencesKey("quick")
    private val ratesKey = booleanPreferencesKey("show_rates")
    private val inputsKey = booleanPreferencesKey("show_inputs")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            dock = p[dockKey]?.split(',')?.filter { it.isNotBlank() } ?: Settings.DEFAULT_DOCK,
            hidden = p[hiddenKey] ?: emptySet(),
            liveTvPackage = p[liveTvKey] ?: "com.tcl.tv",
            disabledSections = p[sectionsOffKey] ?: emptySet(),
            background = p[backgroundKey]?.let { v -> Background.entries.firstOrNull { it.name == v } } ?: Background.NOITE,
            density = p[densityKey] ?: "compacta",
            quick = p[quickKey]?.mapNotNull { v -> QuickAction.entries.firstOrNull { it.name == v } }?.toSet() ?: QuickAction.DEFAULT,
            showRates = p[ratesKey] ?: true,
            showInputs = p[inputsKey] ?: true,
        )
    }

    suspend fun toggleDock(pkg: String) = context.dataStore.edit { p ->
        val cur = p[dockKey]?.split(',')?.filter { it.isNotBlank() } ?: Settings.DEFAULT_DOCK
        p[dockKey] = (if (pkg in cur) cur - pkg else cur + pkg).joinToString(",")
    }

    suspend fun moveDock(pkg: String, delta: Int) = context.dataStore.edit { p ->
        val cur = (p[dockKey]?.split(',')?.filter { it.isNotBlank() } ?: Settings.DEFAULT_DOCK).toMutableList()
        val i = cur.indexOf(pkg); val j = i + delta
        if (i >= 0 && j in cur.indices) { cur[i] = cur[j].also { cur[j] = cur[i] } }
        p[dockKey] = cur.joinToString(",")
    }

    suspend fun toggleHidden(pkg: String) = context.dataStore.edit { p ->
        val cur = p[hiddenKey] ?: emptySet()
        p[hiddenKey] = if (pkg in cur) cur - pkg else cur + pkg
    }

    suspend fun setLiveTv(pkg: String) = context.dataStore.edit { it[liveTvKey] = pkg }

    suspend fun toggleSection(section: Section) = context.dataStore.edit { p ->
        val cur = p[sectionsOffKey] ?: emptySet()
        p[sectionsOffKey] = if (section.name in cur) cur - section.name else cur + section.name
    }

    suspend fun setBackground(bg: Background) = context.dataStore.edit { it[backgroundKey] = bg.name }

    suspend fun toggleDensity() = context.dataStore.edit { p ->
        p[densityKey] = if ((p[densityKey] ?: "compacta") == "compacta") "normal" else "compacta"
    }

    suspend fun toggleQuick(action: QuickAction) = context.dataStore.edit { p ->
        val cur = p[quickKey]?.mapNotNull { v -> QuickAction.entries.firstOrNull { it.name == v } }?.toSet() ?: QuickAction.DEFAULT
        p[quickKey] = (if (action in cur) cur - action else cur + action).map { it.name }.toSet()
    }

    suspend fun toggleRates() = context.dataStore.edit { p -> p[ratesKey] = !(p[ratesKey] ?: true) }
    suspend fun toggleInputs() = context.dataStore.edit { p -> p[inputsKey] = !(p[inputsKey] ?: true) }
}
