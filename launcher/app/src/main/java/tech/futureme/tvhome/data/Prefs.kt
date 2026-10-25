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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tvhome")

/** Fundos predefinidos. "imagem" usa o arquivo enviado por scripts/58-launcher-wallpaper.sh. */
enum class Background(val label: String) {
    HORIZONTE("Horizonte (foto)"), AURORA("Aurora"), OCEANO("Oceano"), OCASO("Ocaso"),
    PRETO("Preto"), GRAFITE("Grafite"), NOITE("Azul-noite"), GRADIENTE("Gradiente"), IMAGEM("Minha imagem");
}

/**
 * Estilo "vidro": painéis translúcidos que deixam o papel de parede atravessar, borda iluminada,
 * reflexo e brilho no foco. Tudo desenhado sobre uma cópia do papel de parede calculada uma vez
 * (sem desfoque em tempo real), então custa o mesmo que um card opaco.
 */
data class Glass(
    val enabled: Boolean = true,
    /** 0 = painel quase opaco, 1 = quase só o papel de parede. */
    val transparency: Float = 0.6f,
    /** 0 = sem reflexos/bordas/brilho, 1 = padrão, 1.5 = exagerado. */
    val fx: Float = 1f,
    /** Desfoque do papel de parede "Minha imagem": 0 nítido … 3 muito desfocado. */
    val blur: Int = 2,
    val animations: Boolean = true,
    /** Modo leve: painéis escuros sem ver-através nem reflexos (fallback para GPU fraca). */
    val lite: Boolean = false,
)

data class Settings(
    /** Pacotes do dock, na ordem. Vazio = os 8 primeiros apps instalados. */
    val dock: List<String> = DEFAULT_DOCK,
    val hidden: Set<String> = emptySet(),
    val liveTvPackage: String = "com.tcl.tv",
    val disabledSections: Set<String> = emptySet(),
    val background: Background = Background.HORIZONTE,
    val glass: Glass = Glass(),
    val density: String = "compacta",
    val quick: Set<QuickAction> = QuickAction.DEFAULT,
    val showRates: Boolean = true,
    val showInputs: Boolean = true,
    val showWeather: Boolean = true,
    val showFast: Boolean = false,          // canais por internet (TCL Channel+) numa linha própria
    val nightMode: Boolean = true,          // escurece após as 21h
    val recents: List<RecentEntry> = emptyList(),
    val latitude: Double = -23.0185,        // Recreio dos Bandeirantes, RJ
    val longitude: Double = -43.4620,
    /** Ordem de preferência dos apps em TODAS as seções (continuar, filmes, séries, sugestões). */
    val appOrder: List<String> = DEFAULT_ORDER,
    val showTeam: Boolean = true,
    val showStatus: Boolean = true,
    val heroCompact: Boolean = false,       // hero sempre no modo compacto
    val lastChannelId: Long = 0L,
    val teamYoutube: List<String> = listOf("UCOa-WaNwQaoyFHLCDk7qKIw"),   // Flamengo TV (canal oficial)
    val teamEspnId: String = "819",
    val teamLeague: String = "bra.1",
    val teamName: String = "Flamengo",
    val pins: Map<String, String> = emptyMap(),   // perfil -> senha (padrão 1234)
) {
    fun pinFor(profileId: String): String = pins[profileId] ?: Profiles.DEFAULT_PIN
    fun appRank(pkg: String): Int = appOrder.indexOf(pkg).let { if (it < 0) 999 else it }
    companion object {
        val DEFAULT_ORDER = listOf(
            "com.netflix.ninja",
            "com.apple.atve.androidtv.appletv",
            "com.google.android.youtube.tv",
            "com.globo.globotv",
            "com.amazon.amazonvideo.livingroom",
            "com.wbd.stream",
            "com.disney.disneyplus",
            "com.spotify.tv.android",
        )
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
    private val weatherKey = booleanPreferencesKey("show_weather")
    private val fastKey = booleanPreferencesKey("show_fast")
    private val nightKey = booleanPreferencesKey("night_mode")
    private val recentsKey = stringPreferencesKey("recents")
    private val latKey = stringPreferencesKey("lat")
    private val lonKey = stringPreferencesKey("lon")
    private val orderKey = stringPreferencesKey("app_order")
    private val teamKey = booleanPreferencesKey("show_team")
    private val statusKey = booleanPreferencesKey("show_status")
    private val heroKey = booleanPreferencesKey("hero_compact")
    private val lastChKey = stringPreferencesKey("last_channel")
    private val teamYtKey = stringPreferencesKey("team_youtube")
    private val pinsKey = stringPreferencesKey("pins")
    private val activeProfileKey = stringPreferencesKey("active_profile")
    private val profileBootKey = stringPreferencesKey("active_profile_boot")
    private val glassOnKey = booleanPreferencesKey("glass_on")
    private val glassTransKey = stringPreferencesKey("glass_transparency")
    private val glassFxKey = stringPreferencesKey("glass_fx")
    private val glassBlurKey = stringPreferencesKey("glass_blur")
    private val glassAnimKey = booleanPreferencesKey("glass_animations")
    private val glassLiteKey = booleanPreferencesKey("glass_lite")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            dock = p[dockKey]?.split(',')?.filter { it.isNotBlank() } ?: Settings.DEFAULT_DOCK,
            hidden = p[hiddenKey] ?: emptySet(),
            liveTvPackage = p[liveTvKey] ?: "com.tcl.tv",
            disabledSections = p[sectionsOffKey] ?: emptySet(),
            background = p[backgroundKey]?.let { v -> Background.entries.firstOrNull { it.name == v } } ?: Background.HORIZONTE,
            glass = Glass(
                enabled = p[glassOnKey] ?: true,
                transparency = p[glassTransKey]?.toFloatOrNull() ?: 0.6f,
                fx = p[glassFxKey]?.toFloatOrNull() ?: 1f,
                blur = p[glassBlurKey]?.toIntOrNull() ?: 2,
                animations = p[glassAnimKey] ?: true,
                lite = p[glassLiteKey] ?: false,
            ),
            density = p[densityKey] ?: "compacta",
            quick = p[quickKey]?.mapNotNull { v -> QuickAction.entries.firstOrNull { it.name == v } }?.toSet() ?: QuickAction.DEFAULT,
            showRates = p[ratesKey] ?: true,
            showInputs = p[inputsKey] ?: true,
            showWeather = p[weatherKey] ?: true,
            showFast = p[fastKey] ?: false,
            nightMode = p[nightKey] ?: true,
            recents = p[recentsKey]?.split('\n')?.mapNotNull { RecentEntry.decode(it) } ?: emptyList(),
            latitude = p[latKey]?.toDoubleOrNull() ?: -23.0185,
            longitude = p[lonKey]?.toDoubleOrNull() ?: -43.4620,
            appOrder = p[orderKey]?.split(',')?.filter { it.isNotBlank() } ?: Settings.DEFAULT_ORDER,
            showTeam = p[teamKey] ?: true,
            showStatus = p[statusKey] ?: true,
            heroCompact = p[heroKey] ?: false,
            lastChannelId = p[lastChKey]?.toLongOrNull() ?: 0L,
            teamYoutube = p[teamYtKey]?.split(',')?.filter { it.isNotBlank() } ?: listOf("UCOa-WaNwQaoyFHLCDk7qKIw"),
            pins = p[pinsKey]?.split(',')?.mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }?.toMap() ?: emptyMap(),
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
    suspend fun toggleWeather() = context.dataStore.edit { p -> p[weatherKey] = !(p[weatherKey] ?: true) }
    suspend fun toggleFast() = context.dataStore.edit { p -> p[fastKey] = !(p[fastKey] ?: false) }
    suspend fun toggleNight() = context.dataStore.edit { p -> p[nightKey] = !(p[nightKey] ?: true) }

    /** Registra um app/conteúdo aberto; mantém os últimos MAX, sem repetidos. */
    suspend fun recordRecent(entry: RecentEntry) = context.dataStore.edit { p ->
        val cur = p[recentsKey]?.split('\n')?.mapNotNull { RecentEntry.decode(it) } ?: emptyList()
        val key = entry.packageName + "|" + (entry.intentUri ?: "")
        val next = listOf(entry) + cur.filter { it.packageName + "|" + (it.intentUri ?: "") != key }
        p[recentsKey] = next.take(RecentEntry.MAX).joinToString("\n") { it.encode() }
    }
    suspend fun clearRecents() = context.dataStore.edit { p -> p[recentsKey] = "" }
    suspend fun toggleTeam() = context.dataStore.edit { p -> p[teamKey] = !(p[teamKey] ?: true) }
    suspend fun toggleStatus() = context.dataStore.edit { p -> p[statusKey] = !(p[statusKey] ?: true) }
    suspend fun toggleHeroCompact() = context.dataStore.edit { p -> p[heroKey] = !(p[heroKey] ?: false) }
    suspend fun setPin(profileId: String, pin: String) = context.dataStore.edit { p ->
        val cur = p[pinsKey]?.split(',')?.mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }?.toMap() ?: emptyMap()
        p[pinsKey] = (cur + (profileId to pin)).entries.joinToString(",") { "${it.key}=${it.value}" }
    }
    suspend fun setLastChannel(id: Long) = context.dataStore.edit { p -> p[lastChKey] = id.toString() }

    /** Move um app na ordem de preferência; apps fora da lista entram no fim ao serem movidos. */
    suspend fun moveOrder(pkg: String, delta: Int) = context.dataStore.edit { p ->
        val cur = (p[orderKey]?.split(',')?.filter { it.isNotBlank() } ?: Settings.DEFAULT_ORDER).toMutableList()
        if (pkg !in cur) cur += pkg
        val i = cur.indexOf(pkg); val j = i + delta
        if (j in cur.indices) { cur[i] = cur[j].also { cur[j] = cur[i] } }
        p[orderKey] = cur.joinToString(",")
    }
    suspend fun toggleInputs() = context.dataStore.edit { p -> p[inputsKey] = !(p[inputsKey] ?: true) }

    /**
     * Perfil escolhido + identificação do boot (instante em que a TV ligou). Se o processo do
     * launcher for morto pelo sistema (memória) e renascer no MESMO boot, o perfil volta sem
     * passar pelo seletor; TV ligada de novo = boot novo = seletor.
     */
    suspend fun setActiveProfile(profileId: String?) = context.dataStore.edit { p ->
        if (profileId == null) { p.remove(activeProfileKey); p.remove(profileBootKey) }
        else { p[activeProfileKey] = profileId; p[profileBootKey] = bootId().toString() }
    }
    suspend fun activeProfileForThisBoot(): String? {
        val p = context.dataStore.data.first()
        val boot = p[profileBootKey]?.toLongOrNull() ?: return null
        return if (kotlin.math.abs(boot - bootId()) < 5_000) p[activeProfileKey] else null
    }
    private fun bootId(): Long = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()

    /** v0.7: quem estava num dos fundos lisos da v0.6 (preto, grafite, azul-noite, gradiente) passa para a foto Horizonte, uma vez só. */
    suspend fun migrateWallpaper() = context.dataStore.edit { p ->
        val done = booleanPreferencesKey("bg_migrated_v7b")
        if (p[done] != true) {
            val old = setOf(Background.NOITE.name, Background.GRADIENTE.name, Background.PRETO.name, Background.GRAFITE.name)
            if (p[backgroundKey] == null || p[backgroundKey] in old) p[backgroundKey] = Background.HORIZONTE.name
            p[done] = true
        }
    }

    suspend fun toggleGlass() = context.dataStore.edit { p -> p[glassOnKey] = !(p[glassOnKey] ?: true) }
    suspend fun toggleGlassAnimations() = context.dataStore.edit { p -> p[glassAnimKey] = !(p[glassAnimKey] ?: true) }
    suspend fun toggleGlassLite() = context.dataStore.edit { p -> p[glassLiteKey] = !(p[glassLiteKey] ?: false) }
    /** Transparência em passos de 0,1 dentro de 0,1..1,0. */
    suspend fun adjustGlassTransparency(delta: Int) = context.dataStore.edit { p ->
        val cur = p[glassTransKey]?.toFloatOrNull() ?: 0.6f
        p[glassTransKey] = (Math.round((cur + delta * 0.1f) * 10f) / 10f).coerceIn(0.1f, 1f).toString()
    }
    /** Intensidade dos efeitos em passos de 0,25 dentro de 0..1,5. */
    suspend fun adjustGlassFx(delta: Int) = context.dataStore.edit { p ->
        val cur = p[glassFxKey]?.toFloatOrNull() ?: 1f
        p[glassFxKey] = (Math.round((cur + delta * 0.25f) * 100f) / 100f).coerceIn(0f, 1.5f).toString()
    }
    suspend fun adjustGlassBlur(delta: Int) = context.dataStore.edit { p ->
        val cur = p[glassBlurKey]?.toIntOrNull() ?: 2
        p[glassBlurKey] = (cur + delta).coerceIn(0, 3).toString()
    }
}
