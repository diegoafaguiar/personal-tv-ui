package tech.futureme.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.MediaItem
import java.text.Normalizer

/** Busca unificada: títulos do Watch Next e das sugestões de todos os apps, e os próprios apps. */
@Composable
fun SearchScreen(
    items: List<MediaItem>,
    apps: List<AppEntry>,
    appLabel: (String) -> String,
    onOpenItem: (MediaItem) -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onSearchYoutube: (String) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val d = LocalDims.current
    val q = remember(query) { fold(query) }
    val hits = remember(q, items) {
        if (q.length < 2) emptyList() else items.filter { fold(it.title).contains(q) || fold(it.description ?: "").contains(q) }
            .distinctBy { it.packageName + it.title.lowercase() }.take(60)
    }
    val appHits = remember(q, apps) { if (q.length < 2) emptyList() else apps.filter { fold(it.label).contains(q) } }

    Column(Modifier.fillMaxSize().padding(rowPadding()).padding(top = 6.dp)) {
        PageHeader("Buscar", "Filmes, séries, vídeos e apps de todos os seus serviços", inset = false)
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).glass(RoundedCornerShape(16.dp), strong = true).padding(16.dp)) {
            if (query.isEmpty()) Text("Digite um título, série, time ou app…", fontSize = 18.sp, color = TvColors.text.copy(alpha = 0.6f))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = TvColors.text, fontSize = 18.sp),
                cursorBrush = SolidColor(TvColors.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (q.length >= 2) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                if (hits.isEmpty() && appHits.isEmpty()) "Nada nos seus apps para “$query”." else "${hits.size} títulos · ${appHits.size} apps",
                fontSize = 14.sp, color = TvColors.text.copy(alpha = 0.78f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            ActionButton(if (query.length > 24) "Buscar no YouTube" else "Buscar “$query” no YouTube", { onSearchYoutube(query) })
        } else if (query.isNotEmpty()) Text("Continue digitando…", fontSize = 14.sp, color = TvColors.text.copy(alpha = 0.6f))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(d.mediaW + 8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(d.gap), verticalArrangement = Arrangement.spacedBy(d.gap),
        ) {
            items(appHits, key = { "app-" + it.packageName }) { AppCard(it) { onOpenApp(it) } }
            items(hits, key = { "${it.packageName}:${it.id}:${it.isWatchNext}" }) { MediaCard(it, appLabel(it.packageName)) { onOpenItem(it) } }
        }
    }
}

/** Minúsculas e sem acentos, para "futebol" achar "Futebol" e "sao" achar "São". */
private fun fold(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
