package io.github.diegoafaguiar.sintonia.ui

import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.diegoafaguiar.sintonia.data.AppEntry
import io.github.diegoafaguiar.sintonia.data.MediaItem
import io.github.diegoafaguiar.sintonia.data.MusicContent
import io.github.diegoafaguiar.sintonia.data.MusicItem
import io.github.diegoafaguiar.sintonia.data.NewsItem
import io.github.diegoafaguiar.sintonia.data.RecentEntry
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
    failed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val side = LocalDims.current.side
    val gap = 12.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)) {
        item {
            PageHeader("Música", if (apps.any { it.packageName == "com.spotify.tv.android" }) "OK numa capa toca no Spotify · OK num vídeo abre no YouTube" else "OK numa capa ou vídeo abre no YouTube")
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
        if (music.clips.isNotEmpty()) {
            item(key = "t-clips") { RowTitle("Clipes das mais tocadas", "no YouTube") }
            item(key = "r-clips") { MediaRow(music.clips, appLabel, onOpenItem, onFocused) }
        }
        if (music.youtube.isNotEmpty()) {
            item(key = "t-ytm") { RowTitle("Vídeos musicais no YouTube", "shows e ao vivo em alta") }
            item(key = "r-ytm") { MediaRow(music.youtube, appLabel, onOpenItem, onFocused) }
        }
        if (music.fetchedAt == 0L) {
            if (failed) item { EmptyState("Sem conexão", "Não foi possível buscar as paradas e os vídeos agora.", "Tentar de novo", onRetry) }
            else item { RowTitle("Sugestões", "carregando as paradas…") }
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
            Modifier.width(620.dp).focusable().glass(RoundedCornerShape(22.dp)).padding(28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(item.topic.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TvColors.accent, letterSpacing = 1.sp)
            Text(item.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, lineHeight = 28.sp)
            Text(
                listOfNotNull(item.source.ifBlank { null }, if (item.publishedMs > 0) SimpleDateFormat("EEEE, d 'de' MMMM 'às' HH:mm", Locale("pt", "BR")).format(Date(item.publishedMs)) else null).joinToString(" · "),
                fontSize = 13.sp, color = TvColors.textDim,
            )
            if (!item.summary.isNullOrBlank()) Text(item.summary, fontSize = 15.sp, color = TvColors.text, lineHeight = 21.sp)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                val qr = remember(item.link) { qrBitmap(item.link, 480) }
                if (qr != null) androidx.compose.foundation.Image(qr, contentDescription = "QR code da matéria", modifier = Modifier.size(172.dp).clip(RoundedCornerShape(10.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Leia a matéria completa no celular", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
                    Text("Aponte a câmera para o código. Abre em ${item.source.ifBlank { "o site da fonte" }}.", fontSize = 13.sp, color = TvColors.text.copy(alpha = 0.75f))
                    Text("Voltar fecha", fontSize = 12.sp, color = TvColors.text.copy(alpha = 0.6f))
                }
            }
        }
    }
}

/** QR code (zxing core) como ImageBitmap branco e preto com margem. */
fun qrBitmap(text: String, sizePx: Int): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(com.google.zxing.EncodeHintType.MARGIN to 2))
    val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    android.graphics.Bitmap.createBitmap(px, m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()
