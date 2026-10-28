package tech.futureme.tvhome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.futureme.tvhome.HomeViewModel
import tech.futureme.tvhome.data.Accent
import tech.futureme.tvhome.data.TileStyle
import tech.futureme.tvhome.data.LabelMode
import tech.futureme.tvhome.data.StylePreset
import tech.futureme.tvhome.data.FontChoice
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.AppTile
import tech.futureme.tvhome.data.Background
import tech.futureme.tvhome.data.FocusStyle
import tech.futureme.tvhome.data.Profile
import tech.futureme.tvhome.data.Profiles
import tech.futureme.tvhome.data.QuickAction
import tech.futureme.tvhome.data.Section
import tech.futureme.tvhome.data.Settings

/** Grupos dos Ajustes (lista da esquerda do painel; OK entra, Voltar sai). */
private enum class Page(val label: String, val hint: String, val icon: ImageVector) {
    ESTILOS("Estilos prontos", "um clique muda o visual inteiro", Icons.Rounded.AutoAwesome),
    APARENCIA("Aparência", "papel de parede, cor de destaque, vidro", Icons.Rounded.Palette),
    CARDS("Cards", "tamanho, espaçamento, formato dos apps", Icons.Rounded.Dashboard),
    LINHAS("Linhas", "espaço, opacidade, linha fixa, ícones", Icons.Rounded.ViewDay),
    FOCO("Card focado", "estilo, escala, borda", Icons.Rounded.CenterFocusStrong),
    TOPO("Topo e destaque", "relógio, cotações, clima, carrossel", Icons.Rounded.Widgets),
    SECOES("Seções da home", "mostrar ou esconder cada linha", Icons.Rounded.ViewAgenda),
    APPS("Apps", "favoritos, ordem, ocultos, TV ao vivo", Icons.Rounded.Apps),
    MENU("Ícones do menu", "atalhos de rede, som, imagem", Icons.Rounded.Tune),
    PERFIS("Perfis", "senhas", Icons.Rounded.Person),
    SISTEMA("Sistema", "configurações da TV, diagnóstico", Icons.Rounded.Settings),
}

/**
 * Ajustes no estilo "painel lateral com prévia": à esquerda a home em miniatura, inclinada, que
 * muda na hora; à direita a lista de ajustes em vidro. ◀ ▶ mexem em sliders e listas, OK liga e
 * desliga, Voltar sai do grupo.
 */
@Composable
fun SettingsScreen(
    vm: HomeViewModel,
    apps: List<AppEntry>,
    settings: Settings,
    onChangePin: (Profile) -> Unit,
    onOpenTvSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenRemote: () -> Unit = {},
) {
    val prefs = vm.prefs
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<Page?>(null) }
    var lastPage by remember { mutableStateOf<Page?>(null) }
    BackHandler(enabled = page != null) { lastPage = page; page = null }
    fun go(b: suspend () -> Unit) { scope.launch { b() } }
    val look = settings.look
    val g = settings.glass

    Row(Modifier.fillMaxSize()) {
        // ---------- prévia ----------
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            LookPreview(settings, apps)
        }
        // ---------- painel ----------
        val firstRow = remember { FocusRequester() }
        LaunchedEffect(page) { delay(120); runCatching { firstRow.requestFocus() } }
        Column(
            Modifier.width(430.dp).fillMaxHeight().padding(top = 16.dp, bottom = 16.dp, end = 24.dp)
                .glass(RoundedCornerShape(26.dp), strong = true).padding(vertical = 14.dp),
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(page?.label ?: "Ajustes", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
                Spacer(Modifier.weight(1f))
                Text(if (page == null) "OK entra · Voltar sai" else "◀ ▶ ajustam · Voltar", fontSize = 12.sp, color = TvColors.textDim)
            }
            val listState = rememberLazyListState()
            LaunchedEffect(page) { listState.scrollToItem(0) }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var first = true
                fun m(): Modifier = if (first) { first = false; Modifier.focusRequester(firstRow) } else Modifier
                when (page) {
                    null -> items(Page.entries, key = { it.name }) { p ->
                        val mod = if (p == (lastPage ?: Page.ESTILOS)) Modifier.focusRequester(firstRow) else Modifier
                        // Estilos: mostra qual está em uso (ou "Personalizado")
                        val hint = if (p == Page.ESTILOS) "em uso: " + (StylePreset.current(settings)?.label ?: "personalizado") else p.hint
                        NavRow(p.icon, p.label, hint, mod) { page = p }
                    }
                    Page.ESTILOS -> {
                        val cur = StylePreset.current(settings)
                        items(StylePreset.entries, key = { "style-" + it.name }) { st ->
                            val mod = if (st == (cur ?: StylePreset.VIDRO)) Modifier.focusRequester(firstRow) else Modifier
                            StyleRow(st, selected = st == cur, modifier = mod) { go { prefs.applyStyle(st) } }
                        }
                        item {
                            Text(
                                if (cur == null) "Visual personalizado: você mudou algum ajuste. Escolher um estilo substitui papel de parede, vidro e todos os ajustes de Aparência."
                                else "Depois de escolher, ajuste o que quiser nas outras páginas; o estilo vira \"personalizado\".",
                                fontSize = 12.sp, color = TvColors.textDim, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Page.APARENCIA -> {
                        item { ChoiceRow(Icons.Rounded.Wallpaper, "Papel de parede", settings.background.label, m(),
                            onPrev = { go { prefs.setBackground(Background.entries[(settings.background.ordinal - 1 + Background.entries.size) % Background.entries.size]) } },
                            onNext = { go { prefs.setBackground(Background.entries[(settings.background.ordinal + 1) % Background.entries.size]) } }) }
                        item { ChoiceRow(Icons.Rounded.Palette, "Cor de destaque", look.accent.label, swatch = Color(look.accent.argb),
                            onPrev = { go { prefs.cycleEnum("look_accent", Accent.entries, Accent.BRANCO, -1) } },
                            onNext = { go { prefs.cycleEnum("look_accent", Accent.entries, Accent.BRANCO, +1) } }) }
                        item { ChoiceRow(Icons.Rounded.TextFields, "Fonte", look.font.label,
                            onPrev = { go { prefs.cycleEnum("look_font", FontChoice.entries, FontChoice.PADRAO, -1) } },
                            onNext = { go { prefs.cycleEnum("look_font", FontChoice.entries, FontChoice.PADRAO, +1) } }) }
                        item { ToggleRow(Icons.Rounded.AutoAwesome, "Estilo vidro", g.enabled) { go { prefs.toggleGlass() } } }
                        item { SliderRow(Icons.Rounded.Opacity, "Transparência dos painéis", (g.transparency * 100).toInt(), 10, 100, 10, "%") { d -> go { prefs.adjustGlassTransparency(d) } } }
                        item { SliderRow(Icons.Rounded.ShowChart, "Intensidade dos reflexos", (g.fx * 100).toInt(), 0, 150, 25, "%") { d -> go { prefs.adjustGlassFx(d) } } }
                        item { SliderRow(Icons.Rounded.BlurOn, "Desfoque da foto de fundo", g.blur, 0, 3, 1, labels = listOf("nítido", "leve", "médio", "forte")) { d -> go { prefs.adjustGlassBlur(d) } } }
                        item { ToggleRow(Icons.Rounded.Speed, "Modo leve (para TV com pouca memória)", g.lite) { go { prefs.toggleGlassLite() } } }
                        item { ToggleRow(Icons.Rounded.TouchApp, "Animações de foco", g.animations) { go { prefs.toggleGlassAnimations() } } }
                        item { ToggleRow(Icons.Rounded.Bedtime, "Modo noturno (escurece das 21h às 6h)", settings.nightMode) { go { prefs.toggleNight() } } }
                        item { ActionRow(Icons.Rounded.RestartAlt, "Restaurar aparência padrão", "cards, linhas, foco e cor") { go { prefs.resetLook() } } }
                    }
                    Page.CARDS -> {
                        item { SliderRow(Icons.Rounded.PhotoSizeSelectLarge, "Tamanho dos cards", Math.round(look.cardScale * 100), 80, 130, 5, "%", m()) { d -> go { prefs.stepFloat("look_card_scale", 1f, 0.05f, 0.8f, 1.3f, d) } } }
                        item { SliderRow(Icons.Rounded.SpaceBar, "Espaço entre cards", look.cardSpacing, 4, 28, 2, " dp") { d -> go { prefs.stepInt("look_card_spacing", 12, 2, 4, 28, d) } } }
                        item { ChoiceRow(Icons.Rounded.AspectRatio, "Formato dos apps", look.appTile.label,
                            onPrev = { go { prefs.cycleEnum("look_app_tile", AppTile.entries, AppTile.ICONE, -1) } },
                            onNext = { go { prefs.cycleEnum("look_app_tile", AppTile.entries, AppTile.ICONE, +1) } }) }
                        item { ChoiceRow(Icons.Rounded.Label, "O que cada app mostra", look.labelMode.label,
                            onPrev = { go { prefs.cycleEnum("look_label_mode", LabelMode.entries, look.labelMode, -1) } },
                            onNext = { go { prefs.cycleEnum("look_label_mode", LabelMode.entries, look.labelMode, +1) } }) }
                        item { ChoiceRow(Icons.Rounded.Palette, "Fundo dos ícones", look.tileStyle.label,
                            onPrev = { go { prefs.cycleEnum("look_tile_style", TileStyle.entries, TileStyle.VIDRO, -1) } },
                            onNext = { go { prefs.cycleEnum("look_tile_style", TileStyle.entries, TileStyle.VIDRO, +1) } }) }
                        item { ChoiceRow(Icons.Rounded.ViewAgenda, "Densidade das linhas de conteúdo", if (settings.density == "normal") "normal (~4 por linha)" else "compacta (~6 por linha)",
                            onPrev = { go { prefs.toggleDensity() } }, onNext = { go { prefs.toggleDensity() } }) }
                    }
                    Page.LINHAS -> {
                        item { SliderRow(Icons.Rounded.DragHandle, "Espaço entre linhas (% da altura)", look.rowSpacing, 0, 8, 1, "%", m()) { d -> go { prefs.stepInt("look_row_spacing", 2, 1, 0, 8, d) } } }
                        item { SliderRow(Icons.Rounded.Opacity, "Opacidade das linhas sem foco", Math.round(look.unselectedAlpha * 100), 30, 100, 10, "%") { d -> go { prefs.stepFloat("look_unselected_alpha", 1f, 0.1f, 0.3f, 1f, d) } } }
                        item { ToggleRow(Icons.Rounded.VerticalAlignCenter, "Linha focada sempre na mesma altura", look.fixedFocusRow) { go { prefs.toggleBool("look_fixed_row", false) } } }
                        item { ToggleRow(Icons.Rounded.VisibilityOff, "Esconder linhas acima da seleção", look.hideRowsAbove) { go { prefs.toggleBool("look_hide_above", false) } } }
                        item { ToggleRow(Icons.Rounded.PushPin, "Lembrar o card focado em cada linha", look.rememberFocus) { go { prefs.toggleBool("look_remember_focus", true) } } }
                        item { ToggleRow(Icons.Rounded.Image, "Ícone antes do título da linha", look.rowIcons) { go { prefs.toggleBool("look_row_icons", true) } } }
                    }
                    Page.FOCO -> {
                        item { ChoiceRow(Icons.Rounded.BorderOuter, "Destaque do card focado", look.focusStyle.label, m(),
                            onPrev = { go { prefs.cycleEnum("look_focus_style", FocusStyle.entries, FocusStyle.BORDA, -1) } },
                            onNext = { go { prefs.cycleEnum("look_focus_style", FocusStyle.entries, FocusStyle.BORDA, +1) } }) }
                        item { SliderRow(Icons.Rounded.ZoomIn, "Aumento do card focado", Math.round(look.focusScale * 100), 100, 120, 2, "%") { d -> go { prefs.stepFloat("look_focus_scale", 1.08f, 0.02f, 1f, 1.2f, d) } } }
                        item { SliderRow(Icons.Rounded.BorderOuter, "Espessura da borda", look.focusBorder, 0, 5, 1, " dp") { d -> go { prefs.stepInt("look_focus_border", 3, 1, 0, 5, d) } } }
                    }
                    Page.TOPO -> {
                        item { ToggleRow(Icons.Rounded.Schedule, "Segundos no relógio", look.clockSeconds, m()) { go { prefs.toggleBool("look_clock_seconds", false) } } }
                        item { ToggleRow(Icons.Rounded.Person, "Saudação com o nome do perfil", look.greeting) { go { prefs.toggleBool("look_greeting", true) } } }
                        item { ToggleRow(Icons.Rounded.ShowChart, "Cotações do dólar e do euro", settings.showRates) { go { prefs.toggleRates() } } }
                        item { ToggleRow(Icons.Rounded.CloudQueue, "Clima (Recreio dos Bandeirantes)", settings.showWeather) { go { prefs.toggleWeather() } } }
                        item { ToggleRow(Icons.Rounded.Info, "Estado da TV (rede, memória) na home clássica", settings.showStatus) { go { prefs.toggleStatus() } } }
                        item { ToggleRow(Icons.Rounded.ViewDay, "Destaque compacto (home clássica)", settings.heroCompact) { go { prefs.toggleHeroCompact() } } }
                    }
                    Page.SECOES -> {
                        var firstSec = true
                        items(Section.entries, key = { "sec-" + it.name }) { s ->
                            val mod = if (firstSec) { firstSec = false; Modifier.focusRequester(firstRow) } else Modifier
                            ToggleRow(rowIconFor(s.title) ?: Icons.Rounded.ViewDay, s.title, s.name !in settings.disabledSections, mod) { go { prefs.toggleSection(s) } }
                        }
                        item { ToggleRow(Icons.Rounded.SportsSoccer, "Seção ${settings.teamName}", settings.showTeam) { go { prefs.toggleTeam() } } }
                        item { ToggleRow(Icons.Rounded.LiveTv, "Canais pela internet (TCL Channel+)", settings.showFast) { go { prefs.toggleFast() } } }
                        item { ToggleRow(Icons.Rounded.SettingsInputHdmi, "Entradas HDMI no menu e no carrossel", settings.showInputs) { go { prefs.toggleInputs() } } }
                    }
                    Page.APPS -> if (apps.isEmpty()) item { ActionRow(Icons.Rounded.Apps, "Carregando os apps…", "Voltar retorna aos ajustes", m()) { page = null } }
                        else appsPage(apps, settings, m(), go = ::go, prefsOps = prefs)
                    Page.MENU -> {
                        var firstQ = true
                        items(QuickAction.entries, key = { "q-" + it.name }) { q ->
                            val mod = if (firstQ) { firstQ = false; Modifier.focusRequester(firstRow) } else Modifier
                            ToggleRow(q.icon, q.label, q in settings.quick, mod) { go { prefs.toggleQuick(q) } }
                        }
                    }
                    Page.PERFIS -> {
                        var firstP = true
                        items(Profiles.ALL.filter { it.requiresPin }, key = { "pin-" + it.id }) { p ->
                            val mod = if (firstP) { firstP = false; Modifier.focusRequester(firstRow) } else Modifier
                            ActionRow(Icons.Rounded.Lock, "Senha de ${p.name}", if (settings.pins.containsKey(p.id)) "personalizada" else "padrão (1234)", mod) { onChangePin(p) }
                        }
                    }
                    Page.SISTEMA -> {
                        item { ActionRow(Icons.Rounded.Settings, "Configurações da TV", "rede, som, imagem, apps", m()) { onOpenTvSettings() } }
                        item { ActionRow(Icons.Rounded.PhoneAndroid, "Controle pelo celular", if (settings.remoteEnabled) "QR code para parear o celular" else "desligado") { onOpenRemote() } }
                        item { ActionRow(Icons.Rounded.Info, "Diagnóstico", "o que cada app publica") { onOpenDiagnostics() } }
                        item { ActionRow(Icons.Rounded.ClearAll, "Limpar abertos recentemente", "${settings.recents.size} itens") { go { prefs.clearRecents() } } }
                    }
                }
            }
        }
    }
}

/** Favoritos, ordem nas seções, ocultos e app de TV ao vivo, no formato do painel. */
private fun LazyListScope.appsPage(apps: List<AppEntry>, settings: Settings, firstMod: Modifier, go: (suspend () -> Unit) -> Unit, prefsOps: tech.futureme.tvhome.data.Prefs) {
    item { GroupLabel("Favoritos (OK põe/tira · ◀ ▶ mudam a posição)") }
    var first = true
    items(apps, key = { "dock-" + it.packageName }) { app ->
        val mod = if (first) { first = false; firstMod } else Modifier
        val pos = settings.dock.indexOf(app.packageName)
        AppSettingRow(app, if (pos >= 0) "favorito ${pos + 1}º" else "OK adiciona", on = pos >= 0, modifier = mod,
            onToggle = { go { prefsOps.toggleDock(app.packageName) } },
            onPrev = { if (pos >= 0) go { prefsOps.moveDock(app.packageName, -1) } },
            onNext = { if (pos >= 0) go { prefsOps.moveDock(app.packageName, +1) } })
    }
    item { GroupLabel("Ordem nas seções (continuar, filmes, séries)") }
    items(apps.sortedBy { settings.appRank(it.packageName) }, key = { "order-" + it.packageName }) { app ->
        val pos = settings.appOrder.indexOf(app.packageName)
        AppSettingRow(app, if (pos >= 0) "${pos + 1}º" else "sem posição", on = pos >= 0,
            onToggle = { go { prefsOps.moveOrder(app.packageName, 0) } },
            onPrev = { go { prefsOps.moveOrder(app.packageName, -1) } },
            onNext = { go { prefsOps.moveOrder(app.packageName, +1) } })
    }
    item { GroupLabel("Apps ocultos (somem da home e de Apps)") }
    items(apps, key = { "hide-" + it.packageName }) { app ->
        val hidden = app.packageName in settings.hidden
        AppSettingRow(app, if (hidden) "oculto" else "visível", on = !hidden, onToggle = { go { prefsOps.toggleHidden(app.packageName) } })
    }
    item { GroupLabel("App de TV ao vivo (atual: ${settings.liveTvPackage})") }
    items(apps.filter { it.packageName.contains("tv", ignoreCase = true) }, key = { "live-" + it.packageName }) { app ->
        AppSettingRow(app, if (app.packageName == settings.liveTvPackage) "em uso" else "", on = app.packageName == settings.liveTvPackage, onToggle = { go { prefsOps.setLiveTv(app.packageName) } })
    }
}

// ---------------------------------------------------------------------------------------------
// Linhas do painel
// ---------------------------------------------------------------------------------------------

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = TvColors.textDim, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp))
}

/** Base: ícone num círculo, título (e valor), à direita o controle. Sem borda: o foco é o fundo claro. */
@Composable
private fun PanelRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    onClick: () -> Unit,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    trailing: @Composable (focused: Boolean) -> Unit = {},
    below: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalLook.current.accentColor
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.fillMaxWidth().clip(shape)
            .background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> if (onLeft != null) { onLeft(); true } else false
                    Key.DirectionRight -> if (onRight != null) { onRight(); true } else false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onClick(); true }
                    else -> false
                }
            }
            .focusable()
            .tapToClick(onClick),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(if (focused) accent.copy(alpha = 0.28f) else Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = if (focused) TvColors.text else TvColors.textDim, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text(title, fontSize = 14.sp, color = if (focused) TvColors.text else TvColors.text.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (sub != null) Text(sub, fontSize = 12.sp, color = if (focused) TvColors.text.copy(alpha = 0.75f) else TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                trailing(focused)
            }
            if (below != null) Box(Modifier.padding(start = 50.dp, top = 6.dp)) { below() }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, hint: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    PanelRow(icon, title, modifier, sub = hint, onClick = onClick, onRight = onClick, trailing = { f ->
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = if (f) TvColors.text else TvColors.textDim, modifier = Modifier.size(22.dp))
    })

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String?, modifier: Modifier = Modifier, onClick: () -> Unit) =
    PanelRow(icon, title, modifier, sub = sub, onClick = onClick)

/** Um estilo pronto: nome, o que ele muda e ✓ quando é o visual em uso. OK aplica. */
@Composable
private fun StyleRow(style: StylePreset, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    PanelRow(Icons.Rounded.AutoAwesome, style.label, modifier, sub = style.hint, onClick = onClick, trailing = { f ->
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "em uso", tint = if (f) TvColors.text else LocalLook.current.accentColor, modifier = Modifier.size(22.dp))
    })

/** Interruptor com ✓ (estilo das imagens de referência). OK alterna. */
@Composable
private fun ToggleRow(icon: ImageVector, title: String, on: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val accent = LocalLook.current.accentColor
    PanelRow(icon, title, modifier, onClick = onToggle, trailing = {
        Box(Modifier.width(52.dp).height(28.dp).clip(RoundedCornerShape(50)).background(if (on) accent.copy(alpha = 0.45f) else Color(0x33FFFFFF)), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(3.dp).size(22.dp).clip(CircleShape).background(if (on) Color.White else Color(0xFF8B98A5)), contentAlignment = Alignment.Center) {
                if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = accent.copy(red = accent.red * 0.6f, green = accent.green * 0.6f, blue = accent.blue * 0.6f), modifier = Modifier.size(14.dp))
            }
        }
    })
}

/**
 * Slider de pontos: um ponto por passo, a barra cheia até o valor, o traço vertical marca a
 * posição. ◀ ▶ (ou OK, que avança) ajustam.
 */
@Composable
private fun SliderRow(
    icon: ImageVector, title: String, value: Int, min: Int, max: Int, step: Int, unit: String = "",
    modifier: Modifier = Modifier, labels: List<String>? = null, onStep: (Int) -> Unit,
) {
    val accent = LocalLook.current.accentColor
    val steps = ((max - min) / step).coerceAtLeast(1)
    val idx = ((value - min) / step).coerceIn(0, steps)
    val valueText = labels?.getOrNull(idx) ?: "$value$unit"
    PanelRow(icon, title, modifier, onClick = { onStep(if (idx >= steps) -steps else +1) }, onLeft = { onStep(-1) }, onRight = { onStep(+1) },
        trailing = { Text(valueText, fontSize = 12.sp, color = TvColors.textDim) },
        below = {
            Row(Modifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0..steps) {
                    val filled = i <= idx
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (i == idx) Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                        else Box(Modifier.size(5.dp).clip(CircleShape).background(if (filled) accent else Color(0x55FFFFFF)))
                    }
                }
            }
        })
}

/** Lista de opções: valor atual com ▾; ◀ ▶ trocam, OK avança. */
@Composable
private fun ChoiceRow(icon: ImageVector, title: String, value: String, modifier: Modifier = Modifier, swatch: Color? = null, onPrev: () -> Unit, onNext: () -> Unit) =
    PanelRow(icon, title, modifier, sub = value, onClick = onNext, onLeft = onPrev, onRight = onNext, trailing = { f ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (swatch != null) Box(Modifier.padding(end = 6.dp).size(18.dp).clip(CircleShape).background(swatch).border(BorderStroke(1.dp, Color(0x66FFFFFF)), CircleShape))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = if (f) TvColors.text else TvColors.textDim, modifier = Modifier.size(22.dp))
        }
    })

@Composable
private fun AppSettingRow(app: AppEntry, value: String, on: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit, onPrev: (() -> Unit)? = null, onNext: (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalLook.current.accentColor
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> if (onPrev != null) { onPrev(); true } else false
                    Key.DirectionRight -> if (onNext != null) { onNext(); true } else false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onToggle(); true }
                    else -> false
                }
            }
            .focusable().tapToClick(onToggle),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = app.icon ?: app.banner, contentDescription = null, modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
            Text(app.label, fontSize = 14.sp, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Text(value, fontSize = 12.sp, color = if (on) accent else TvColors.textDim)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Prévia
// ---------------------------------------------------------------------------------------------

/**
 * Miniatura da home, inclinada como nas referências, desenhada com os ajustes atuais: tamanho e
 * espaço dos cards, espaço entre linhas, opacidade das linhas sem foco, linhas acima escondidas,
 * estilo e escala do card focado, cor de destaque, formato e nome dos apps, vidro e papel de parede.
 * Não é focável: só mostra.
 */
@Composable
private fun LookPreview(settings: Settings, apps: List<AppEntry>) {
    val look = settings.look
    val accent = Color(look.accent.argb)
    val density = LocalDensity.current
    val sample = apps.filter { it.packageName !in settings.hidden }.let { list -> settings.dock.mapNotNull { p -> list.firstOrNull { it.packageName == p } } + list }.distinct().take(12)
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.padding(start = 40.dp, end = 12.dp).fillMaxWidth().height(330.dp)
            .graphicsLayer { rotationY = 12f; scaleX = 0.92f; scaleY = 0.92f; cameraDistance = 14f * density.density; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f) }
            .glass(shape).padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (look.clockSeconds) "21:40:12" else "21:40", fontSize = 20.sp, fontWeight = FontWeight.Light, color = TvColors.text)
                Spacer(Modifier.width(10.dp))
                if (settings.showRates) Box(Modifier.glass(RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("R$ 4,97", fontSize = 10.sp, color = TvColors.text) }
            }
            val rowGap = (look.rowSpacing * 2.2f).dp + 6.dp
            // acima, focada, abaixo (com poucos apps, repete a lista para preencher a miniatura)
            val many = if (sample.isEmpty()) sample else generateSequence { sample }.flatten().take(14).toList()
            val rows = listOf(many.drop(4), many, many.drop(2))
            for ((ri, list) in rows.withIndex()) {
                val alpha = when {
                    ri == 1 -> 1f
                    ri == 0 && look.hideRowsAbove -> 0f
                    else -> look.unselectedAlpha
                }
                Column(Modifier.padding(top = rowGap).graphicsLayer { this.alpha = alpha }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (look.rowIcons) Icon(Icons.Rounded.Apps, contentDescription = null, tint = accent.copy(alpha = 0.9f), modifier = Modifier.padding(end = 4.dp).size(10.dp))
                        Text(listOf("Continuar assistindo", "Seus aplicativos", "Lançamentos")[ri], fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
                    }
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy((look.cardSpacing * 0.45f).dp)) {
                        for ((ci, app) in list.take(7).withIndex().toList()) PreviewTile(app, look, accent, focused = ri == 1 && ci == 1, banner = look.appTile == AppTile.BANNER || ri != 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTile(app: AppEntry, look: tech.futureme.tvhome.data.Look, accent: Color, focused: Boolean, banner: Boolean) {
    val base: Dp = 34.dp * look.cardScale
    val w = if (banner) base * 1.7f else base
    val scale = if (focused) look.focusScale else 1f
    val shape = RoundedCornerShape(8.dp)
    val showBorder = focused && look.focusStyle != FocusStyle.BRILHO && look.focusBorder > 0
    val showGlow = focused && look.focusStyle != FocusStyle.BORDA
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(w)) {
        Box(
            Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                .then(if (showGlow) Modifier.background(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent)), shape) else Modifier)
                .size(w, base).clip(shape).background(if (banner) Color(0x55FFFFFF) else tileBackground(look.tileStyle, appColor(app)) ?: Color(0x55FFFFFF))
                .then(if (showBorder) Modifier.border(BorderStroke((look.focusBorder * 0.6f).dp, accent), shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val img = if (banner) app.banner ?: app.icon else app.icon ?: app.banner
            if (!banner && look.labelMode == LabelMode.NOME) Text(app.label, fontSize = 6.sp, color = TvColors.text, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            else if (img != null) AsyncImage(model = img, contentDescription = null, modifier = Modifier.fillMaxSize().padding(if (banner) 0.dp else 5.dp), contentScale = if (banner) ContentScale.Crop else ContentScale.Fit)
        }
        if (!banner && look.labelMode == LabelMode.ICONE_NOME) Text(app.label, fontSize = 6.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(y = 2.dp))
    }
}
