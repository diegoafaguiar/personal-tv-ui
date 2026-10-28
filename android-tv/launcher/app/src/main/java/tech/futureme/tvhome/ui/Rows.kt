package tech.futureme.tvhome.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import android.media.tv.TvContract
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.InputEntry
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.data.MusicItem
import tech.futureme.tvhome.data.NewsItem
import tech.futureme.tvhome.data.TunerChannel

val CardShape = RoundedCornerShape(10.dp)

/** Início das linhas quando algo já ocupa a esquerda (barra lateral da home de vidro). null = zona segura. */
val LocalRowStart = compositionLocalOf<Dp?> { null }

/** Padding lateral das linhas (zona segura), segue a densidade escolhida. */
@Composable
fun rowPadding(): PaddingValues = PaddingValues(start = LocalRowStart.current ?: LocalDims.current.side, end = LocalDims.current.side, top = 12.dp, bottom = 12.dp)

/** Só a zona segura lateral (hero, títulos, menu de ícones). */
@Composable
fun sidePadding(): PaddingValues = PaddingValues(start = LocalRowStart.current ?: LocalDims.current.side, end = LocalDims.current.side)

/**
 * Ícone quadrado de app com acabamento de vidro e rótulo embaixo (estilo da home de vidro).
 * Usa android:icon do app; sem ícone, as iniciais sobre a cor do nome.
 */
@Composable
fun AppIcon(app: AppEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val color = remember(app.packageName) { appColor(app) }
    val shape = RoundedCornerShape(20.dp)
    val look = LocalLook.current
    val side = 84.dp * look.cardScale
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(side)) {
        FocusSurface(
            onClick = onClick, shape = shape, glowColor = color,
            modifier = modifier.size(side).onFocusChanged { if (it.isFocused) glass.focusColor = color },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (app.icon != null) {
                    AsyncImage(model = app.icon, contentDescription = app.label, modifier = Modifier.size(side * 0.67f).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                } else {
                    Box(Modifier.size(side * 0.67f).clip(RoundedCornerShape(14.dp)).background(placeholderBrush(app.packageName)), contentAlignment = Alignment.Center) {
                        Text(app.label.take(2).uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TvColors.text)
                    }
                }
                if (glass.config.enabled && !glass.config.lite && glass.config.fx > 0f) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f * glass.config.fx), 0.45f to Color.Transparent)))
                }
            }
        }
        if (look.appLabels) Text(app.label, fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Gradiente estável derivado do nome: dois tons da mesma matiz, para placeholders com identidade. */
fun placeholderBrush(seed: String): Brush {
    val hue = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
    return Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.42f), Color.hsv((hue + 30f) % 360f, 0.6f, 0.18f)))
}

/**
 * Toque/clique do mouse = foco + clique. O Surface do Compose for TV só responde ao OK do D-pad;
 * na TV não há toque (nada muda), mas no emulador o mouse passa a funcionar como no celular.
 */
fun Modifier.tapToClick(onClick: () -> Unit): Modifier = composed {
    val focus = remember { FocusRequester() }
    focusRequester(focus).pointerInput(onClick) {
        detectTapGestures {
            runCatching { focus.requestFocus() }
            onClick()
        }
    }
}

/** Superfície focável padrão: escala 1.08 e borda branca no foco. Semitransparente para o fundo aparecer. */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CardShape,
    glowColor: Color = Color.Transparent,
    /** Escala máxima para este card (o hero usa 1,02); a escolhida em Aparência vale até esse teto. */
    focusScale: Float? = null,
    content: @Composable () -> Unit,
) {
    val glass = LocalGlass.current.config
    val look = LocalLook.current
    var focused by remember { mutableStateOf(false) }
    val wanted = focusScale?.let { minOf(it, look.focusScale) } ?: look.focusScale
    val scale = if (glass.enabled && !glass.animations) 1f else wanted
    val border = if (look.focusStyle == tech.futureme.tvhome.data.FocusStyle.BRILHO) 0 else look.focusBorder
    val glow = if (look.focusStyle == tech.futureme.tvhome.data.FocusStyle.BORDA) glowColor
        else if (glowColor.alpha > 0f) glowColor else look.accentColor
    Surface(
        onClick = onClick,
        modifier = modifier.focusGlow(focused, glow).onFocusChanged { focused = it.isFocused }.tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = if (glass.enabled) ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent)
        else ClickableSurfaceDefaults.colors(containerColor = Color(0x99151B23), focusedContainerColor = Color(0xCC1F2733)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = scale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (border > 0) Border(border = BorderStroke(border.dp, look.accentColor), shape = shape) else Border.None,
        ),
    ) {
        // O vidro é desenhado DENTRO do Surface para escalar junto com o foco.
        Box(Modifier.fillMaxSize().glass(shape).focusSweep(focused)) { content() }
    }
}

@Composable
fun RowTitle(text: String, subtitle: String? = null, icon: ImageVector? = null) {
    val d = LocalDims.current
    val look = LocalLook.current
    // Espaço entre linhas em % da altura da tela (~540 dp numa TV 1080p)
    val top = 16.dp + (look.rowSpacing * 5.4f).dp
    Row(Modifier.padding(sidePadding()).padding(top = top, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        val ic = icon ?: rowIconFor(text)
        if (ic != null && look.rowIcons) {
            Icon(ic, contentDescription = null, tint = look.accentColor.copy(alpha = 0.9f), modifier = Modifier.padding(end = 10.dp).size((d.titleSp + 2).dp))
        }
        Column {
            Text(text, fontSize = d.titleSp.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
            if (subtitle != null) Text(subtitle, fontSize = (d.titleSp - 8).sp, color = TvColors.textDim)
        }
    }
}

/**
 * Comportamento das linhas escolhido em Aparência: opacidade das linhas sem foco, esconder as
 * de cima e lembrar o card focado. [id] agrupa título e linha; [order] é a posição na tela.
 */
class RowFocusState {
    var id by mutableStateOf<String?>(null)
    var order by mutableStateOf(-1)
}
val LocalRowFocus = compositionLocalOf<RowFocusState?> { null }

fun Modifier.rowStyle(id: String, order: Int): Modifier = composed {
    val st = LocalRowFocus.current ?: return@composed this
    val look = LocalLook.current
    this
        .onFocusChanged { if (it.hasFocus) { st.id = id; st.order = order } }
        // Lido na fase de desenho: trocar de linha não recompõe a lista
        .graphicsLayer {
            val cur = st.id
            alpha = when {
                cur == null || cur == id -> 1f
                look.hideRowsAbove && order < st.order -> 0f
                else -> look.unselectedAlpha
            }
        }
}

/**
 * Envolve um LazyListScope: cada item ganha [rowStyle], agrupado pela chave ("title-x" e "row-x"
 * formam o grupo "x"). Assim a home inteira obedece Aparência sem mexer em cada linha.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class StyledRows(
    private val base: androidx.compose.foundation.lazy.LazyListScope,
    /** Rolagem padrão para as linhas horizontais (a "linha fixa" vale só para a lista vertical). */
    private val innerSpec: androidx.compose.foundation.gestures.BringIntoViewSpec? = null,
) : androidx.compose.foundation.lazy.LazyListScope by base {
    private val orders = HashMap<String, Int>()
    private fun group(key: Any?): String = (key?.toString() ?: "anon-${orders.size}").removePrefix("title-").removePrefix("row-")
    private fun orderOf(g: String) = orders.getOrPut(g) { orders.size }
    override fun item(key: Any?, contentType: Any?, content: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit) {
        val g = group(key); val o = orderOf(g)
        val spec = innerSpec
        base.item(key, contentType) {
            val scope = this
            Box(Modifier.rowStyle(g, o)) {
                if (spec == null) scope.content()
                else CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec) { scope.content() }
            }
        }
    }
}

/** Ícone de cada linha (Aparência › Ícone antes do título), pelo título. */
fun rowIconFor(title: String): ImageVector? {
    val t = title.lowercase()
    return when {
        t.startsWith("continuar") -> Icons.Rounded.PlayCircle
        t.contains("flamengo") || t.contains("futebol") -> Icons.Rounded.SportsSoccer
        t.contains("tv aberta") || t.contains("tv globo") || t.contains("canais") -> Icons.Rounded.LiveTv
        t == "filmes" -> Icons.Rounded.Movie
        t == "séries" -> Icons.Rounded.Tv
        t.contains("para você") || t.contains("destaques") -> Icons.Rounded.AutoAwesome
        t.contains("lançamentos") -> Icons.Rounded.NewReleases
        t.contains("tecnologia") -> Icons.Rounded.Memory
        t.contains("recentes") || t.contains("recentemente") -> Icons.Rounded.History
        t.contains("aplicativos") || t.contains("apps") -> Icons.Rounded.Apps
        t.contains("música") || t.contains("tocadas") || t.contains("playlists") || t.contains("álbuns") || t.contains("artistas") || t.contains("ouvindo") -> Icons.Rounded.MusicNote
        t.contains("clipe") || t.contains("vídeo") || t.contains("youtube") -> Icons.Rounded.SmartDisplay
        t.contains("esta home") || t.contains("ajustes") -> Icons.Rounded.Settings
        else -> Icons.Rounded.ViewCarousel
    }
}

/** Voltar a esta linha foca o último card visitado (Aparência › Lembrar o card focado). */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.rememberFocus(): Modifier = composed {
    if (LocalLook.current.rememberFocus) this.focusRestorer() else this
}

@Composable
fun MediaRow(items: List<MediaItem>, appLabel: (String) -> String, onClick: (MediaItem) -> Unit, onFocused: (MediaItem?) -> Unit = {}) {
    LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
        items(items, key = { "${it.packageName}:${it.id}:${it.isWatchNext}" }) { item ->
            MediaCard(item, appLabel(item.packageName), onFocused = onFocused) { onClick(item) }
        }
    }
}

@Composable
fun MediaCard(item: MediaItem, appLabel: String, onFocused: (MediaItem?) -> Unit = {}, onClick: () -> Unit) {
    val d = LocalDims.current
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.width(d.mediaW).height(d.mediaH).onFocusChanged { if (it.isFocused) onFocused(item) },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item.imageUri != null) {
                AsyncImage(model = item.imageUri, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                // Placeholder com a identidade do app: gradiente pelo nome + nome do app grande
                Box(Modifier.fillMaxSize().background(placeholderBrush(item.packageName)), contentAlignment = Alignment.Center) {
                    Text(appLabel, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xCCFFFFFF))
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xCC000000))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = if (item.progress != null) 14.dp else 10.dp)) {
                Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(appLabel, item.progressLabel).joinToString(" · ")
                Text(sub, fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.progress != null) {
                // Barra de progresso do Watch Next (posição / duração publicadas pelo app)
                Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color(0x55FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress).background(Color.White))
                }
            }
        }
    }
}

/**
 * Tile de app: o banner que o app publica, com acabamento de vidro por cima (reflexo no topo,
 * borda iluminada) e brilho na cor dominante do banner quando focado. Com o vidro desligado,
 * só o banner.
 */
@Composable
fun AppCard(app: AppEntry, glassy: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val d = LocalDims.current
    val glass = LocalGlass.current
    val color = remember(app.packageName) { appColor(app) }
    FocusSurface(
        onClick = onClick, glowColor = color,
        modifier = modifier.width(d.appW).height(d.appH).onFocusChanged { if (it.isFocused) glass.focusColor = color },
    ) {
        if (app.banner != null && app.hasBanner) {
            AsyncImage(
                model = app.banner, contentDescription = app.label,
                modifier = Modifier.fillMaxSize().clip(CardShape), contentScale = ContentScale.Crop,
                alpha = if (glassy && glass.config.enabled) 0.88f else 1f,
            )
        } else {
            // Sem banner 16:9: ícone do app e o nome, sobre a cor do ícone
            Row(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color.copy(alpha = 0.35f), Color(0x66101418)))).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (app.icon != null) AsyncImage(model = app.icon, contentDescription = null, modifier = Modifier.size(d.appH * 0.5f), contentScale = ContentScale.Fit)
                Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (glass.config.enabled && !glass.config.lite && glass.config.fx > 0f) {
            // Acabamento de vidro sobre o banner: reflexo em cima, sombra interna embaixo
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.28f * glass.config.fx), 0.35f to Color.Transparent,
                        0.8f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f * glass.config.fx),
                    ),
                ),
            )
        }
    }
}

/** Botão de texto, mesmo tamanho dos apps. */
@Composable
fun PillCard(label: String, onClick: () -> Unit, sub: String? = null) {
    val d = LocalDims.current
    FocusSurface(onClick = onClick, modifier = Modifier.width(d.pillW).height(d.pillH)) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun InputCard(input: InputEntry, onClick: () -> Unit) =
    PillCard(label = input.label, onClick = onClick, sub = if (input.isTuner) "TV ao vivo" else "entrada")

/**
 * Canal de TV: logo do canal (o TvProvider guarda em channel/<id>/logo; o Channel+
 * costuma preencher, as emissoras abertas nem sempre), senão número grande sobre a cor do canal.
 */
@Composable
fun ChannelCard(ch: TunerChannel, onClick: () -> Unit) {
    val d = LocalDims.current
    var logoFailed by remember(ch.id) { mutableStateOf(false) }
    FocusSurface(onClick = onClick, modifier = Modifier.width(d.mediaW).height(d.mediaH)) {
        Box(Modifier.fillMaxSize().background(placeholderBrush(ch.name))) {
            if (!logoFailed) {
                AsyncImage(
                    model = TvContract.buildChannelLogoUri(ch.id), contentDescription = ch.name,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp), contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                )
            }
            if (logoFailed) Text(ch.number, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0x55FFFFFF), modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000))))
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(ch.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (ch.nowTitle != null) "Agora: ${ch.nowTitle}" else if (ch.isBroadcast) "TV aberta" else "ao vivo pela internet",
                    fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (ch.nextTitle != null) Text("Depois: ${ch.nextTitle}", fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Ícone limpo: sem fundo em repouso; no foco vira um círculo claro com o ícone escuro e o rótulo embaixo. */
@Composable
fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        val glass = LocalGlass.current.config
        val round = RoundedCornerShape(50)
        Surface(
            onClick = onClick,
            modifier = Modifier.size(44.dp).clip(CircleShape).onFocusChanged { focused = it.isFocused }.tapToClick(onClick),
            shape = ClickableSurfaceDefaults.shape(round),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = TvColors.text,
                contentColor = TvColors.textDim,
                focusedContentColor = TvColors.background,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (glass.enabled && !glass.animations) 1f else 1.15f),
            border = ClickableSurfaceDefaults.border(),
        ) {
            // Em repouso: bolha de vidro discreta; no foco o Surface pinta o círculo claro por cima.
            Box(Modifier.fillMaxSize().then(if (glass.enabled && !focused) Modifier.glass(round) else Modifier), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = if (focused) TvColors.background else TvColors.textDim, modifier = Modifier.size(22.dp))
            }
        }
        Text(if (focused) label else " ", fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Manchete: glass, assunto, título em até 3 linhas, fonte e "há 2 h". OK abre a leitura. */
@Composable
fun NewsCard(item: NewsItem, onClick: () -> Unit) {
    val d = LocalDims.current
    FocusSurface(onClick = onClick, modifier = Modifier.width(d.mediaW).height(d.mediaH)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(item.topic.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TvColors.accent, letterSpacing = 1.sp)
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Text(listOfNotNull(item.source.ifBlank { null }, relativeTime(item.publishedMs)).joinToString(" · "), fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun relativeTime(ms: Long): String? {
    if (ms <= 0) return null
    val min = (System.currentTimeMillis() - ms) / 60_000L
    return when {
        min < 1 -> "agora"
        min < 60 -> "há $min min"
        min < 60 * 24 -> "há ${min / 60} h"
        else -> "há ${min / (60 * 24)} d"
    }
}

/** Sugestão musical: capa quadrada, título, artista, tipo. OK abre a busca no app de música. */
@Composable
fun MusicCard(item: MusicItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.width(116.dp)) {
        FocusSurface(onClick = onClick, shape = shape, modifier = Modifier.size(116.dp)) {
            Box(Modifier.fillMaxSize()) {
                if (item.imageUrl != null) AsyncImage(model = item.imageUrl, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(placeholderBrush(item.title)))
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(item.kind.label, fontSize = 11.sp, color = TvColors.text, letterSpacing = 0.5.sp)
                }
            }
        }
        Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(item.subtitle, fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Card de informação do time: próximo jogo, último resultado, posição. */
@Composable
fun TeamInfoCard(name: String, info: tech.futureme.tvhome.data.FlamengoRepository.TeamInfo?, onClick: () -> Unit) {
    val d = LocalDims.current
    FocusSurface(onClick = onClick, modifier = Modifier.width(d.mediaW).height(d.mediaH)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF8B0000), Color(0xFF1A1A1A))))) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (info == null) {
                    Text("Sem dados agora", fontSize = 13.sp, color = Color(0xAAFFFFFF))
                } else {
                    Column {
                        val n = info.next
                        if (n != null) {
                            val f = java.text.SimpleDateFormat("EEE d/M HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(n.dateMs))
                            Text("Próximo: ${if (n.home) "vs" else "@"} ${n.opponent}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$f · ${n.competition}", fontSize = 12.sp, color = Color(0xAAFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val l = info.last
                        if (l != null) Text("Último: ${l.score} ${if (l.home) "vs" else "@"} ${l.opponent}", fontSize = 12.sp, color = Color(0xAAFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (info.position != null) Text("${info.position}º · ${info.points ?: "-"} pts · ${info.played ?: "-"} jogos", fontSize = 12.sp, color = Color(0xAAFFFFFF))
                    }
                }
            }
        }
    }
}


/** Programa da grade: horário em destaque, título, sinopse curta. */
@Composable
fun ProgramCard(p: tech.futureme.tvhome.data.ProgramEntry, isNow: Boolean, onClick: () -> Unit) {
    val d = LocalDims.current
    val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR"))
    FocusSurface(onClick = onClick, modifier = Modifier.width(d.mediaW).height(d.mediaH)) {
        Column(Modifier.fillMaxSize().background(if (isNow) Brush.linearGradient(listOf(Color(0xFF1B5E20), Color(0xFF0B0F14))) else placeholderBrush("globo")).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(if (isNow) "AGORA · ${f.format(java.util.Date(p.startMs))}–${f.format(java.util.Date(p.endMs))}" else "${f.format(java.util.Date(p.startMs))}–${f.format(java.util.Date(p.endMs))}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isNow) Color(0xFFA5D6A7) else TvColors.textDim)
            Column {
                Text(p.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!p.description.isNullOrBlank()) Text(p.description, fontSize = 12.sp, color = TvColors.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}


/**
 * Rolagem de TV "mínima": só rola o necessário para o item focado caber, com uma margem para o
 * brilho/escala do foco não encostar na borda. (O padrão do Compose em TVs fixa o foco a 30% da
 * tela, o que desce a home sozinha no primeiro foco e corta o destaque.)
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
val MinimalScroll = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = 36f
        val leading = offset - margin
        val trailing = offset + size + margin
        return when {
            leading >= 0 && trailing <= containerSize -> 0f
            leading < 0 || size + 2 * margin > containerSize -> leading
            else -> trailing - containerSize
        }
    }
}

/** Cabeçalho padrão das páginas: título grande, uma linha de ajuda. Mesma posição em todas. */
@Composable
fun PageHeader(title: String, hint: String? = null, inset: Boolean = true) {
    Column(Modifier.then(if (inset) Modifier.padding(sidePadding()) else Modifier).padding(top = 6.dp, bottom = 6.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
        if (hint != null) Text(hint, fontSize = 13.sp, color = TvColors.text.copy(alpha = 0.72f))
    }
}

/** Estado vazio com uma próxima ação (nunca uma página sem saída). */
@Composable
fun EmptyState(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.padding(sidePadding()).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
        Text(text, fontSize = 14.sp, color = TvColors.text.copy(alpha = 0.72f))
        if (action != null) Box(Modifier.padding(top = 6.dp)) { ActionButton(action, onAction) }
    }
}


/** Botão em pílula, largura pelo texto (ações de estados vazios, avisos). */
@Composable
fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(50), focusScale = 1.05f, modifier = modifier.height(48.dp)) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TvColors.text, maxLines = 1)
        }
    }
}


/** O clima não respondeu (sem internet). */
val LocalFeedFailed = compositionLocalOf { false }
