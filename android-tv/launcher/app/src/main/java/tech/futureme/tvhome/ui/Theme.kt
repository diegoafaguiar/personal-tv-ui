package tech.futureme.tvhome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import tech.futureme.tvhome.data.Look
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Paleta escura, contraste alto, para 3 metros de distância. */
object TvColors {
    val background = Color(0xFF0B0F14)
    val surface = Color(0xFF151B23)
    val surfaceHigh = Color(0xFF1F2733)
    val text = Color(0xFFE6EDF3)
    val textDim = Color(0xFF8B98A5)
    val accent = Color(0xFF5BC0EB)
    val focus = Color(0xFFFFFFFF)
}

@Composable
fun TvHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = TvColors.background,
            surface = TvColors.surface,
            surfaceVariant = TvColors.surfaceHigh,
            onSurface = TvColors.text,
            onSurfaceVariant = TvColors.textDim,
            primary = TvColors.accent,
            onPrimary = Color.Black,
            border = Color(0x33FFFFFF),
        ),
        content = content,
    )
}

/** Aparência escolhida em Ajustes › Aparência (tamanho, espaços, foco, cor de destaque…). */
val LocalLook = compositionLocalOf { Look() }
val Look.accentColor: Color get() = Color(accent.argb)

/** Variação de preço: subida verde, queda vermelha, estável apagado (convenção de mercado). */
fun changeColor(pct: Double): androidx.compose.ui.graphics.Color = when {
    pct > 0 -> androidx.compose.ui.graphics.Color(0xFF81C784)
    pct < 0 -> androidx.compose.ui.graphics.Color(0xFFE57373)
    else -> TvColors.textDim
}
