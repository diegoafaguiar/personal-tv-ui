package tech.futureme.tvhome.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.InputEntry
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.data.TunerChannel

val CardShape = RoundedCornerShape(10.dp)

/** Padding lateral das linhas (zona segura), segue a densidade escolhida. */
@Composable
fun rowPadding(): PaddingValues = PaddingValues(horizontal = LocalDims.current.side)

/** Gradiente estável derivado do nome: dois tons da mesma matiz, para placeholders com identidade. */
fun placeholderBrush(seed: String): Brush {
    val hue = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
    return Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.42f), Color.hsv((hue + 30f) % 360f, 0.6f, 0.18f)))
}

/** Superfície focável padrão: escala 1.08 e borda branca no foco. Semitransparente para o fundo aparecer. */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CardShape,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x99151B23),
            focusedContainerColor = Color(0xCC1F2733),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, TvColors.focus), shape = shape),
        ),
    ) { content() }
}

@Composable
fun RowTitle(text: String, subtitle: String? = null) {
    val d = LocalDims.current
    Column(Modifier.padding(rowPadding()).padding(top = 22.dp, bottom = 10.dp)) {
        Text(text, fontSize = d.titleSp.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
        if (subtitle != null) Text(subtitle, fontSize = (d.titleSp - 8).sp, color = TvColors.textDim)
    }
}

@Composable
fun MediaRow(items: List<MediaItem>, appLabel: (String) -> String, onClick: (MediaItem) -> Unit, onFocused: (MediaItem?) -> Unit = {}) {
    LazyRow(contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
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
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(appLabel, item.episodeTitle).joinToString(" · ")
                Text(sub, fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun AppCard(app: AppEntry, onClick: () -> Unit) {
    val d = LocalDims.current
    FocusSurface(onClick = onClick, modifier = Modifier.width(d.appW).height(d.appH)) {
        if (app.banner != null) {
            AsyncImage(model = app.banner, contentDescription = app.label, modifier = Modifier.fillMaxSize().clip(CardShape), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(placeholderBrush(app.packageName)), contentAlignment = Alignment.Center) {
                Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
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

/** Canal de TV: número grande, nome, e "agora" quando há grade; fundo com a cor do canal. */
@Composable
fun ChannelCard(ch: TunerChannel, onClick: () -> Unit) {
    val d = LocalDims.current
    FocusSurface(onClick = onClick, modifier = Modifier.width(d.mediaW).height(d.mediaH)) {
        Box(Modifier.fillMaxSize().background(placeholderBrush(ch.name))) {
            Text(ch.number, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0x55FFFFFF), modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(ch.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (ch.nowTitle != null) "Agora: ${ch.nowTitle}" else if (ch.isBroadcast) "TV aberta" else "ao vivo pela internet",
                    fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (ch.nextTitle != null) Text("Depois: ${ch.nextTitle}", fontSize = 11.sp, color = TvColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Botão redondo só com ícone; o rótulo aparece embaixo quando focado. */
@Composable
fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        FocusSurface(onClick = onClick, shape = RoundedCornerShape(50), modifier = Modifier.size(52.dp).clip(CircleShape).onFocusChanged { focused = it.isFocused }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = TvColors.text, modifier = Modifier.size(26.dp))
            }
        }
        Text(if (focused) label else " ", fontSize = 12.sp, color = TvColors.textDim, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}
