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

/** Cores de destaque (borda e brilho do foco, detalhes). */
enum class Accent(val label: String, val argb: Long) {
    BRANCO("Branco", 0xFFFFFFFF), CIANO("Ciano", 0xFF5BC0EB), AZUL("Azul", 0xFF6C8CFF), VIOLETA("Violeta", 0xFFB18CFF),
    ROSA("Rosa", 0xFFFF7EB6), CORAL("Coral", 0xFFFF8A65), AMBAR("Âmbar", 0xFFFFC857), VERDE("Verde", 0xFF6EE7A8), RUBRO("Rubro-negro", 0xFFE53935);
}

/** Como os apps aparecem em "Seus aplicativos". */
enum class AppTile(val label: String) { ICONE("Ícone quadrado (1/1)"), BANNER("Banner do app (16/9)") }

/** O que o bloco de cada app mostra. */
enum class LabelMode(val label: String) { ICONE_NOME("Ícone e nome"), ICONE("Só o ícone"), NOME("Só o nome") }

/** Fundo do bloco de cada app em "Seus aplicativos". */
enum class TileStyle(val label: String) { VIDRO("Vidro"), MARCA("Cor da marca"), ESCURO("Escuro liso") }

/** Formato do destaque do card focado. */
enum class FocusStyle(val label: String) { BORDA("Borda"), BRILHO("Brilho, sem borda"), BORDA_BRILHO("Borda e brilho") }

/**
 * Personalização da aparência (Ajustes › Aparência). Tudo vale na hora, com prévia ao lado.
 * Os valores ficam em passos para serem ajustados com ◀ ▶ no controle.
 */
data class Look(
    /** Tamanho dos cards: 0,8 a 1,3 (1 = padrão). */
    val cardScale: Float = 1f,
    /** Espaço entre cards, em dp: 4 a 28. */
    val cardSpacing: Int = 12,
    /** Espaço entre linhas, em % da altura da tela: 0 a 8. */
    val rowSpacing: Int = 2,
    /** Opacidade das linhas sem foco: 0,3 a 1. */
    val unselectedAlpha: Float = 1f,
    /** A linha focada fica sempre na mesma altura da tela (a lista anda por baixo). */
    val fixedFocusRow: Boolean = false,
    /** Linhas acima da focada somem (fica só o que vem pela frente). */
    val hideRowsAbove: Boolean = false,
    /** Voltar a uma linha foca o último card visitado nela. */
    val rememberFocus: Boolean = true,
    /** Ícone antes do título de cada linha. */
    val rowIcons: Boolean = true,
    /** Escala do card focado: 1,0 a 1,2. */
    val focusScale: Float = 1.08f,
    /** Espessura da borda de foco, em dp: 0 a 5. */
    val focusBorder: Int = 3,
    val focusStyle: FocusStyle = FocusStyle.BORDA,
    val accent: Accent = Accent.BRANCO,
    val appTile: AppTile = AppTile.ICONE,
    /** O que cada app mostra: ícone e nome, só ícone ou só o nome. */
    val labelMode: LabelMode = LabelMode.ICONE_NOME,
    /** Fundo do bloco de cada app: vidro, a cor da marca ou escuro liso. */
    val tileStyle: TileStyle = TileStyle.VIDRO,
    /** "Boa noite, Diego" na linha da data. */
    val greeting: Boolean = true,
    /** Segundos no relógio. */
    val clockSeconds: Boolean = false,
)

data class Settings(
    /** Pacotes do dock, na ordem. Vazio = os 8 primeiros apps instalados. */
    val dock: List<String> = DEFAULT_DOCK,
    val hidden: Set<String> = emptySet(),
    val liveTvPackage: String = "com.tcl.tv",
    val disabledSections: Set<String> = emptySet(),
    val background: Background = Background.HORIZONTE,
    val glass: Glass = Glass(),
    val look: Look = Look(),
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
    /**
     * TV com pouca memória (menos de 1,5 GB, ex.: TCL G13 com 1 GB): o "modo leve" vira o padrão,
     * porque o vidro completo passa de 90 ms por quadro nessas GPUs. O ajuste continua valendo.
     */
    val lowRam: Boolean = run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        am.isLowRamDevice || mi.totalMem < 1_500L * 1024 * 1024
    }

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
            look = Look(
                cardScale = p[floatKey("look_card_scale")] ?: 1f,
                cardSpacing = p[intKey("look_card_spacing")] ?: 12,
                rowSpacing = p[intKey("look_row_spacing")] ?: 2,
                unselectedAlpha = p[floatKey("look_unselected_alpha")] ?: 1f,
                fixedFocusRow = p[booleanPreferencesKey("look_fixed_row")] ?: false,
                hideRowsAbove = p[booleanPreferencesKey("look_hide_above")] ?: false,
                rememberFocus = p[booleanPreferencesKey("look_remember_focus")] ?: true,
                rowIcons = p[booleanPreferencesKey("look_row_icons")] ?: true,
                focusScale = p[floatKey("look_focus_scale")] ?: 1.08f,
                focusBorder = p[intKey("look_focus_border")] ?: 3,
                focusStyle = p[stringPreferencesKey("look_focus_style")]?.let { v -> FocusStyle.entries.firstOrNull { it.name == v } } ?: FocusStyle.BORDA,
                accent = p[stringPreferencesKey("look_accent")]?.let { v -> Accent.entries.firstOrNull { it.name == v } } ?: Accent.BRANCO,
                appTile = p[stringPreferencesKey("look_app_tile")]?.let { v -> AppTile.entries.firstOrNull { it.name == v } } ?: AppTile.ICONE,
                // look_app_labels (liga/desliga, até a v0.9.2) vira "Só o ícone" quando estava desligado
                labelMode = p[stringPreferencesKey("look_label_mode")]?.let { v -> LabelMode.entries.firstOrNull { it.name == v } }
                    ?: if (p[booleanPreferencesKey("look_app_labels")] == false) LabelMode.ICONE else LabelMode.ICONE_NOME,
                tileStyle = p[stringPreferencesKey("look_tile_style")]?.let { v -> TileStyle.entries.firstOrNull { it.name == v } } ?: TileStyle.VIDRO,
                greeting = p[booleanPreferencesKey("look_greeting")] ?: true,
                clockSeconds = p[booleanPreferencesKey("look_clock_seconds")] ?: false,
            ),
            glass = Glass(
                enabled = p[glassOnKey] ?: true,
                transparency = p[glassTransKey]?.toFloatOrNull() ?: 0.6f,
                fx = p[glassFxKey]?.toFloatOrNull() ?: 1f,
                blur = p[glassBlurKey]?.toIntOrNull() ?: 2,
                animations = p[glassAnimKey] ?: true,
                lite = p[glassLiteKey] ?: lowRam,
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

    // ---------- Aparência (Look): ajustes genéricos por chave ----------
    private fun floatKey(n: String) = androidx.datastore.preferences.core.floatPreferencesKey(n)
    private fun intKey(n: String) = androidx.datastore.preferences.core.intPreferencesKey(n)

    /** Soma [delta] passos de [step] a uma chave Float, presa em [min]..[max]. */
    suspend fun stepFloat(name: String, default: Float, step: Float, min: Float, max: Float, delta: Int) = context.dataStore.edit { p ->
        val k = floatKey(name); val cur = p[k] ?: default
        p[k] = (Math.round((cur + delta * step) * 100f) / 100f).coerceIn(min, max)
    }
    suspend fun stepInt(name: String, default: Int, step: Int, min: Int, max: Int, delta: Int) = context.dataStore.edit { p ->
        val k = intKey(name); p[k] = ((p[k] ?: default) + delta * step).coerceIn(min, max)
    }
    suspend fun toggleBool(name: String, default: Boolean) = context.dataStore.edit { p ->
        val k = booleanPreferencesKey(name); p[k] = !(p[k] ?: default)
    }
    /** Próximo (delta=+1) ou anterior (-1) valor de um enum guardado por nome. */
    suspend fun <E : Enum<E>> cycleEnum(name: String, values: List<E>, default: E, delta: Int) = context.dataStore.edit { p ->
        val k = stringPreferencesKey(name)
        val i = values.indexOfFirst { it.name == (p[k] ?: default.name) }.coerceAtLeast(0)
        p[k] = values[(i + delta + values.size) % values.size].name
    }
    suspend fun resetLook() = context.dataStore.edit { p ->
        p.asMap().keys.filter { it.name.startsWith("look_") }.forEach { p.remove(it) }
    }

    /**
     * Aplica um estilo pronto: troca o papel de parede, o vidro e todos os ajustes de Aparência
     * (look_*) de uma vez. O modo leve só muda no estilo "Leve" (nos outros fica como está, para
     * não pesar uma TV com pouca memória).
     */
    suspend fun applyStyle(style: StylePreset) = context.dataStore.edit { p ->
        p.asMap().keys.filter { it.name.startsWith("look_") }.forEach { p.remove(it) }
        val l = style.look
        p[floatKey("look_card_scale")] = l.cardScale
        p[intKey("look_card_spacing")] = l.cardSpacing
        p[intKey("look_row_spacing")] = l.rowSpacing
        p[floatKey("look_unselected_alpha")] = l.unselectedAlpha
        p[booleanPreferencesKey("look_fixed_row")] = l.fixedFocusRow
        p[booleanPreferencesKey("look_hide_above")] = l.hideRowsAbove
        p[booleanPreferencesKey("look_remember_focus")] = l.rememberFocus
        p[booleanPreferencesKey("look_row_icons")] = l.rowIcons
        p[floatKey("look_focus_scale")] = l.focusScale
        p[intKey("look_focus_border")] = l.focusBorder
        p[stringPreferencesKey("look_focus_style")] = l.focusStyle.name
        p[stringPreferencesKey("look_accent")] = l.accent.name
        p[stringPreferencesKey("look_app_tile")] = l.appTile.name
        p[stringPreferencesKey("look_label_mode")] = l.labelMode.name
        p[stringPreferencesKey("look_tile_style")] = l.tileStyle.name
        p[booleanPreferencesKey("look_greeting")] = l.greeting
        p[booleanPreferencesKey("look_clock_seconds")] = l.clockSeconds
        p[backgroundKey] = style.background.name
        p[glassOnKey] = true
        p[glassTransKey] = style.transparency.toString()
        p[glassFxKey] = style.fx.toString()
        p[glassAnimKey] = style.animations
        if (style.lite) p[glassLiteKey] = true
    }

    suspend fun toggleGlass() = context.dataStore.edit { p -> p[glassOnKey] = !(p[glassOnKey] ?: true) }
    suspend fun toggleGlassAnimations() = context.dataStore.edit { p -> p[glassAnimKey] = !(p[glassAnimKey] ?: true) }
    suspend fun toggleGlassLite() = context.dataStore.edit { p -> p[glassLiteKey] = !(p[glassLiteKey] ?: lowRam) }
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
