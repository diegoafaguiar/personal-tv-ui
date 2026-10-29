package io.github.diegoafaguiar.sintonia.ui

import android.media.tv.TvContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.diegoafaguiar.sintonia.data.MediaItem

/**
 * Categorias que juntam o conteúdo de todos os apps. Como os apps quase nunca publicam gênero
 * (diagnóstico de 22/10), a regra combina tipo do programa, gênero quando existe e palavras do
 * título/sinopse. Um item pode estar em várias categorias.
 */
enum class Category(val label: String, val icon: ImageVector) {
    DESTAQUES("Em alta", Icons.Rounded.Whatshot),
    FILMES("Filmes", Icons.Rounded.Movie),
    SERIES("Séries", Icons.Rounded.Tv),
    INFANTIL("Infantil", Icons.Rounded.ChildCare),
    ESPORTES("Esportes", Icons.Rounded.SportsSoccer),
    DOCUMENTARIOS("Documentários", Icons.Rounded.Forest),
    NOVELAS("Novelas", Icons.Rounded.Favorite),
    ANIMES("Animes", Icons.Rounded.AutoAwesome),
    COMEDIA("Comédia", Icons.Rounded.SentimentVerySatisfied),
    ACAO("Ação e suspense", Icons.Rounded.LocalFireDepartment),
    MUSICA("Música", Icons.Rounded.MusicNote),
    ;

    companion object {
        private val KIDS = listOf("patrulha", "masha", "peppa", "bluey", "pokémon", "pokemon", "galinha pintadinha", "bita", "disney junior", "infantil", "kids", "criança", "desenho", "minions", "toy story", "frozen", "moana", "paw patrol", "phineas", "chefinho", "sereno", "cocomelon", "barbie", "lego", "fuga das galinhas", "ursinho", "turma da mônica", "mickey", "super wings")
        private val DOCS = listOf("documentário", "documentary", "unseen", "natureza", "wildlife", "planeta", "oceano", "national geographic", "nat geo", "a história d", "história real", "true crime", "crime real", "selvagem")
        private val NOVELA_WORDS = listOf("novela", "capítulo", "malhação", "vale tudo", "sol da minha vida", "mania de você", "dona de mim")
        private val ANIME = listOf("anime", "naruto", "one piece", "dragon ball", "bleach", "demon slayer", "jujutsu", "my hero", "attack on titan", "crunchyroll", "boruto", "chainsaw")
        private val SPORTS = listOf("futebol", "campeonato", "brasileirão", "libertadores", "copa", "ufc", "nba", "nfl", "fórmula 1", "f1", "tênis", "vôlei", "esporte", "jogo ao vivo", "melhores momentos", "gols", "flamengo", "palmeiras", "corinthians", "vasco", "fluminense", "botafogo", "ronaldinho")
        private val COMEDY = listOf("comédia", "comedy", "stand-up", "standup", "humor", "porta dos fundos", "sitcom")
        private val ACTION = listOf("ação", "action", "missão", "suspense", "thriller", "velozes", "guerra", "assalto", "espião", "terror", "the boys", "reacher", "jack ryan")

        fun of(item: MediaItem): Set<Category> {
            val text = (item.title + " " + (item.description ?: "") + " " + (item.episodeTitle ?: "") + " " + (item.channelName ?: "")).lowercase()
            val g = item.genres.map { it.uppercase() }
            val out = HashSet<Category>()
            fun has(words: List<String>) = words.any { text.contains(it) }
            when (item.type) {
                TvContract.PreviewPrograms.TYPE_MOVIE -> out += FILMES
                TvContract.PreviewPrograms.TYPE_TV_SERIES, TvContract.PreviewPrograms.TYPE_TV_SEASON, TvContract.PreviewPrograms.TYPE_TV_EPISODE -> out += SERIES
            }
            if (has(KIDS) || "FAMILY_KIDS" in g || "FAMILY" in g) out += INFANTIL
            if (has(DOCS) || "ANIMAL_WILDLIFE" in g || "EDUCATION" in g || "TECH_SCIENCE" in g || "TRAVEL" in g) out += DOCUMENTARIOS
            if (has(NOVELA_WORDS)) out += NOVELAS
            if (has(ANIME)) out += ANIMES
            if (has(SPORTS) || "SPORTS" in g) out += ESPORTES
            if (has(COMEDY) || "COMEDY" in g) out += COMEDIA
            if (has(ACTION)) out += ACAO
            if ("MUSIC" in g || text.contains("clipe") || text.contains("ao vivo) ")) out += MUSICA
            return out
        }
    }
}

/** Agrupa todos os itens por categoria; "Em alta" = os mais bem ranqueados de tudo. */
fun groupByCategory(items: List<MediaItem>, rank: (String) -> Int): Map<Category, List<MediaItem>> {
    val uniq = items.filter { it.imageUri != null }.distinctBy { it.packageName + "|" + it.title.lowercase() }
    val sorted = uniq.sortedWith(compareBy<MediaItem>({ if (it.isWatchNext) 0 else 1 }, { rank(it.packageName) }))
    val map = LinkedHashMap<Category, MutableList<MediaItem>>()
    map[Category.DESTAQUES] = sorted.take(24).toMutableList()
    for (it in sorted) for (c in Category.of(it)) map.getOrPut(c) { mutableListOf() } += it
    return Category.entries.filter { (map[it]?.size ?: 0) > 0 }.associateWith { map[it]!!.toList() }
}

/**
 * Explorar: uma faixa de categorias no topo (mover o foco entre elas troca a página, sem OK),
 * o destaque da categoria, as principais recomendações de todos os apps e uma linha por app.
 * Baixo entra no conteúdo; Voltar no conteúdo volta para a faixa; Voltar na faixa sai.
 */
@Composable
fun ExploreScreen(
    groups: Map<Category, List<MediaItem>>,
    initial: Category,
    appLabel: (String) -> String,
    rank: (String) -> Int,
    onOpenItem: (MediaItem) -> Unit,
    onFocused: (MediaItem?) -> Unit,
    onCategoryChanged: (Category) -> Unit,
) {
    val cats = groups.keys.toList()
    var selected by remember(cats) { mutableStateOf(if (initial in cats) initial else cats.firstOrNull() ?: Category.DESTAQUES) }
    var pending by remember { mutableStateOf(selected) }
    // Troca de página com o foco, com um respiro de 220 ms para não recarregar a cada tecla
    LaunchedEffect(pending) { delay(220); if (pending != selected) { selected = pending; onCategoryChanged(pending) } }
    val chipFocus = remember { FocusRequester() }
    val featureFocus = remember { FocusRequester() }
    val emptyFocus = remember { FocusRequester() }
    var chipsHaveFocus by remember { mutableStateOf(true) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(cats.isNotEmpty()) { delay(150); runCatching { if (cats.isNotEmpty()) chipFocus.requestFocus() else emptyFocus.requestFocus() } }
    BackHandler(enabled = !chipsHaveFocus) {
        scope.launch { list.animateScrollToItem(0); delay(60); runCatching { chipFocus.requestFocus() } }
    }
    val items = groups[selected].orEmpty()
    val side = LocalRowStart.current ?: LocalDims.current.side

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp)) {
        item(key = "head") {
            PageHeader("Explorar", "◀ ▶ trocam a categoria · ▼ entra no conteúdo · Voltar volta às categorias")
        }
        item(key = "chips") {
            LazyRow(
                // ▼ sempre vai para o destaque (a geometria escolheria o card mais perto da pílula)
                Modifier.rememberFocus().onFocusChanged { chipsHaveFocus = it.hasFocus }
                    .focusProperties { down = featureFocus },
                contentPadding = PaddingValues(start = side, end = LocalDims.current.side, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(cats, key = { it.name }) { c ->
                    CategoryChip(c, groups[c]?.size ?: 0, c == selected, if (c == selected) Modifier.focusRequester(chipFocus) else Modifier,
                        onFocus = { pending = c }) { pending = c }
                }
            }
        }
        val top = items.firstOrNull()
        if (top == null) {
            item {
                if (cats.isEmpty()) Box(Modifier.focusRequester(emptyFocus)) {
                    EmptyState("Ainda sem conteúdo", "Os apps publicam aqui o que você assiste e as novidades. Abra um app de streaming e volte.", "Atualizar") { onCategoryChanged(Category.DESTAQUES) }
                } else EmptyState("Nada nesta categoria ainda", "Os apps ainda não publicaram nada que se encaixe aqui. Escolha outra categoria acima.")
            }
        } else {
            item(key = "feature-${selected.name}") { FeatureCard(top, appLabel(top.packageName), side, onFocused, Modifier.focusRequester(featureFocus)) { onOpenItem(top) } }
            item(key = "t-top-${selected.name}") { RowTitle(if (selected == Category.DESTAQUES) "Principais destaques" else "Principais em ${selected.label.lowercase()}", "de todos os seus apps", icon = selected.icon) }
            item(key = "r-top-${selected.name}") { MediaRow(items.drop(1).take(20), appLabel, onOpenItem, onFocused) }
            val byApp = items.groupBy { it.packageName }.entries.sortedBy { rank(it.key) }.filter { it.value.size >= 2 }
            for ((pkg, list) in byApp) {
                item(key = "t-${selected.name}-$pkg") { RowTitle("${selected.label} no ${appLabel(pkg)}") }
                item(key = "r-${selected.name}-$pkg") { MediaRow(list.take(20), appLabel, onOpenItem, onFocused) }
            }
        }
    }
}

@Composable
private fun CategoryChip(c: Category, count: Int, selected: Boolean, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalLook.current.accentColor
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(50), focusScale = 1.05f, modifier = modifier.height(40.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }) {
        Row(
            Modifier.fillMaxSize().then(if (selected && !focused) Modifier.background(accent.copy(alpha = 0.22f)) else Modifier).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(c.icon, contentDescription = null, tint = if (selected || focused) TvColors.text else TvColors.textDim, modifier = Modifier.size(18.dp))
            Text(c.label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected || focused) TvColors.text else TvColors.textDim)
            Text("$count", fontSize = 12.sp, color = TvColors.textDim)
        }
    }
}

/** Destaque da categoria: arte larga, app, título, sinopse e "Assistir". */
@Composable
private fun FeatureCard(item: MediaItem, app: String, side: androidx.compose.ui.unit.Dp, onFocused: (MediaItem?) -> Unit, focusModifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.dp), focusScale = 1.02f,
        modifier = focusModifier.padding(start = side, end = LocalDims.current.side, top = 4.dp, bottom = 6.dp).fillMaxWidth().height(200.dp)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused(item) }) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = item.imageUri, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE60B0F14), 0.6f to Color(0x330B0F14), 1f to Color.Transparent)))
            Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 26.dp).fillMaxWidth(0.55f)) {
                Text(app.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TvColors.textDim, letterSpacing = 1.sp)
                Text(item.title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 32.sp)
                val sub = item.progressLabel ?: item.description
                if (!sub.isNullOrBlank()) Text(sub, fontSize = 13.sp, color = TvColors.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (focused) TvColors.text else Color(0x33FFFFFF)).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = if (focused) TvColors.background else TvColors.text, modifier = Modifier.size(18.dp))
                    Text(if (item.isWatchNext) "Continuar" else "Assistir", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (focused) TvColors.background else TvColors.text)
                }
            }
        }
    }
}

/** Faixa de categorias para a home: cada pílula abre o Explorar já na categoria. */
@Composable
fun CategoryStrip(groups: Map<Category, List<MediaItem>>, onOpen: (Category) -> Unit) {
    LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(groups.keys.toList(), key = { "cat-" + it.name }) { c ->
            CategoryChip(c, groups[c]?.size ?: 0, selected = false, modifier = Modifier, onFocus = {}) { onOpen(c) }
        }
    }
}
