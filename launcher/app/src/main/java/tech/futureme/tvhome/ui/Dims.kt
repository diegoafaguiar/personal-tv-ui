package tech.futureme.tvhome.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tamanhos dos cards. Numa TV 1080p o Compose enxerga ~960x540 dp, então
 * "compacta" cabe ~6 apps por linha e "normal" ~4. Zona segura: 5% nas bordas.
 */
data class Dims(
    val appW: Dp, val appH: Dp,
    val mediaW: Dp, val mediaH: Dp,
    val pillW: Dp, val pillH: Dp,
    val gap: Dp,
    val side: Dp,
    val titleSp: Int,
) {
    companion object {
        val NORMAL = Dims(appW = 200.dp, appH = 112.dp, mediaW = 300.dp, mediaH = 200.dp, pillW = 200.dp, pillH = 112.dp, gap = 16.dp, side = 48.dp, titleSp = 24)
        val COMPACT = Dims(appW = 148.dp, appH = 84.dp, mediaW = 232.dp, mediaH = 130.dp, pillW = 148.dp, pillH = 84.dp, gap = 12.dp, side = 40.dp, titleSp = 20)
        fun of(name: String) = if (name == "normal") NORMAL else COMPACT
    }
}

val LocalDims = compositionLocalOf { Dims.COMPACT }
