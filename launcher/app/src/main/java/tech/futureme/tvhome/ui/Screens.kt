package tech.futureme.tvhome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.data.MusicContent
import tech.futureme.tvhome.data.MusicItem
import tech.futureme.tvhome.data.NewsItem
import tech.futureme.tvhome.data.RecentEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Música: seus apps, o que você abriu por último neles, e sugestões (mais tocadas, playlists,
 * álbuns, artistas) das paradas. OK numa sugestão busca no Spotify (ou YouTube).
 */
@Composable
fun MusicScreen(
    apps: List<AppEntry>,
    music: MusicContent,
    continueItems: List<MediaItem>,
    recents: List<RecentEntry>,
    appLabel: (String) -> String,
    onOpenApp: (AppEntry) -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onOpenRecent: (RecentEntry) -> Unit,
    onOpenMusic: (MusicItem) -> Unit,
    onFocused: (MediaItem?) -> Unit,
) {
    val side = LocalDims.current.side
    val gap = 12.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)) {
        item {
            Column(Modifier.padding(start = LocalRowStart.current ?: side, end = side)) {
                Text("Música", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
                Text(
                    if (apps.any { it.packageName == "com.spotify.tv.android" }) "OK numa sugestão abre a busca no Spotify" else "OK numa sugestão abre a busca no YouTube",
                    fontSize = 13.sp, color = TvColors.textDim,
                )
            }
        }
        if (apps.isNotEmpty()) {
            item(key = "apps-title") { RowTitle("Seus apps de música") }
            item(key = "apps") { LazyRow(contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(gap)) { items(apps, key = { it.packageName }) { AppIcon(it) { onOpenApp(it) } } } }
        }
        if (continueItems.isNotEmpty()) {
            item(key = "t-cont") { RowTitle("Continuar ouvindo", "o que seus apps publicaram") }
            item(key = "r-cont") { MediaRow(continueItems, appLabel, onOpenItem, onFocused) }
        }
        if (recents.isNotEmpty()) {
            item(key = "t-rec") { RowTitle("Abertos recentemente") }
            item(key = "r-rec") {
                LazyRow(contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items(recents, key = { it.encode() }) { r -> PillCard(r.title.ifBlank { appLabel(r.packageName) }, { onOpenRecent(r) }, sub = appLabel(r.packageName)) }
                }
            }
        }
        if (music.fetchedAt == 0L) {
            item { RowTitle("Sugestões", "carregando as paradas… (precisa de internet)") }
        } else {
            for ((title, sub, list) in listOf(
                Triple("Mais tocadas no Brasil", "paradas de hoje", music.tracks),
                Triple("Playlists para você", "seleções editoriais", music.playlists),
                Triple("Álbuns em alta", null, music.albums),
                Triple("Artistas do momento", null, music.artists),
            )) {
                if (list.isEmpty()) continue
                item(key = "t-$title") { RowTitle(title, sub) }
                item(key = "r-$title") {
                    LazyRow(contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        items(list, key = { it.kind.name + it.title + it.subtitle }) { MusicCard(it) { onOpenMusic(it) } }
                    }
                }
            }
        }
    }
}

/** Leitura de uma manchete quando não há navegador na TV: título, fonte, data e resumo. */
@Composable
fun ReaderScreen(item: NewsItem) {
    val side = LocalDims.current.side
    Box(Modifier.fillMaxSize().padding(start = (LocalRowStart.current ?: side), end = side, top = 24.dp, bottom = 24.dp)) {
        Column(
            Modifier.width(620.dp).glass(RoundedCornerShape(22.dp)).padding(28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(item.topic.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TvColors.accent, letterSpacing = 1.sp)
            Text(item.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, lineHeight = 28.sp)
            Text(
                listOfNotNull(item.source.ifBlank { null }, if (item.publishedMs > 0) SimpleDateFormat("EEEE, d 'de' MMMM 'às' HH:mm", Locale("pt", "BR")).format(Date(item.publishedMs)) else null).joinToString(" · "),
                fontSize = 13.sp, color = TvColors.textDim,
            )
            if (!item.summary.isNullOrBlank()) Text(item.summary, fontSize = 15.sp, color = TvColors.text, lineHeight = 21.sp)
            Text("Esta TV não tem navegador: leia a matéria completa no celular, em ${item.source.ifBlank { "o site da fonte" }}. Voltar fecha.", fontSize = 12.sp, color = TvColors.textDim, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
