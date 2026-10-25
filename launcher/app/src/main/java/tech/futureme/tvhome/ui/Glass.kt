package tech.futureme.tvhome.ui

import android.graphics.Bitmap
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.futureme.tvhome.R
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.Background
import tech.futureme.tvhome.data.Glass
import java.io.File

/**
 * Estado compartilhado do estilo vidro.
 *
 * Como funciona: o papel de parede é renderizado UMA vez num bitmap de meia resolução
 * (gradientes) ou lido e desfocado uma vez (imagem). O fundo da tela desenha esse bitmap, e
 * cada painel de vidro desenha o MESMO bitmap recortado na própria forma, deslocado pela
 * posição do painel na tela. O olho vê "o fundo atravessando o vidro", mas a GPU só faz uma
 * cópia de textura por painel: nada de desfoque por quadro, que numa TV de 2 GB com Mali
 * derrubaria os 60 fps em 4K.
 */
class GlassState {
    var screen by mutableStateOf(IntSize.Zero)
    /** Fundo da tela (foto nítida, levemente escurecida; gradientes como são). */
    var wallpaper by mutableStateOf<ImageBitmap?>(null)
    /** O que os painéis mostram atravessando o vidro (foto desfocada; gradientes = o mesmo bitmap). */
    var wallpaperBlur by mutableStateOf<ImageBitmap?>(null)
    var config by mutableStateOf(Glass())
    /** Escurecimento extra aplicado ao fundo (modo noturno); os painéis acompanham. */
    var darken by mutableStateOf(0f)
    /** Cor do app/item focado; tinge o fundo e o brilho do foco. */
    var focusColor by mutableStateOf(Color.Transparent)
}

val LocalGlass = staticCompositionLocalOf { GlassState() }

/** Papel de parede renderizado: [sharp] para o fundo, [blur] para dentro dos painéis. */
class Wallpaper(val sharp: ImageBitmap, val blur: ImageBitmap)

/**
 * Renderiza o papel de parede no tamanho pedido (meia resolução da tela). Gradientes: um bitmap
 * só. Fotos (Horizonte embutida ou "Minha imagem"): a nítida escurecida de leve + a desfocada
 * para os painéis. Chame fora da main thread.
 */
suspend fun renderWallpaper(context: Context, bg: Background, imageFile: File?, width: Int, height: Int, blur: Int): Wallpaper = withContext(Dispatchers.Default) {
    val photo: Bitmap? = when {
        bg == Background.HORIZONTE -> decodeScaled({ opts -> BitmapFactory.decodeResource(context.resources, R.drawable.wallpaper_horizonte, opts) }, width)
        bg == Background.IMAGEM && imageFile != null && imageFile.exists() -> decodeScaled({ opts -> BitmapFactory.decodeFile(imageFile.path, opts) }, width)
        else -> null
    }
    if (photo != null) {
        Wallpaper(sharp = photoBitmap(photo, width, height, blur = 0, darken = 0x40000000), blur = photoBitmap(photo, width, height, blur = maxOf(1, blur), darken = 0x66000000))
    } else {
        val img = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(width.toFloat(), height.toFloat())) {
            drawGradientWallpaper(bg)
            drawVignette()
        }
        Wallpaper(img, img)
    }
}

/** Decodifica já reduzido (inSampleSize) para perto da largura pedida: menos RAM, mais rápido. */
private fun decodeScaled(decode: (BitmapFactory.Options) -> Bitmap?, width: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= width) sample *= 2
    return decode(BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
}

/** Desfoque barato: reduz e amplia com filtro bilinear duas vezes (blur 0 = só redimensiona). Escurece e assa a vinheta. */
private fun photoBitmap(decoded: Bitmap, width: Int, height: Int, blur: Int, darken: Int): ImageBitmap {
    val factor = when (blur) { 0 -> 1; 1 -> 5; 2 -> 10; else -> 20 }
    val small = if (factor > 1) Bitmap.createScaledBitmap(decoded, maxOf(1, width / factor), maxOf(1, height / factor), true) else decoded
    val mid = if (factor > 1) Bitmap.createScaledBitmap(small, maxOf(1, width / (factor / 2 + 1)), maxOf(1, height / (factor / 2 + 1)), true) else small
    // createScaledBitmap devolve o próprio bitmap (imutável) quando o tamanho já bate; precisa de cópia mutável
    val scaled = Bitmap.createScaledBitmap(mid, width, height, true)
    val full = if (scaled.isMutable && scaled !== decoded) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true)
    android.graphics.Canvas(full).drawColor(darken)
    val img = full.asImageBitmap()
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(width.toFloat(), height.toFloat())) { drawVignette() }
    return img
}

/** Vinheta: assada no bitmap do papel de parede (custa zero por quadro). */
private fun DrawScope.drawVignette() {
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color(0x8C000000)), center = Offset(size.width * 0.5f, size.height * 0.4f), radius = size.width * 0.75f))
}

private fun DrawScope.blob(cx: Float, cy: Float, r: Float, color: Color) {
    drawRect(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = Offset(size.width * cx, size.height * cy), radius = size.width * r))
}

/** Os mesmos gradientes do protótipo HTML, em coordenadas relativas ao tamanho. */
fun DrawScope.drawGradientWallpaper(bg: Background) {
    val w = size.width; val h = size.height
    when (bg) {
        Background.PRETO -> drawRect(Color.Black)
        Background.GRAFITE -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0D12), Color(0xFF15181F)), endY = h))
            blob(0.3f, 0.3f, 0.45f, Color(0xFF2A2F3A)); blob(0.8f, 0.8f, 0.45f, Color(0xFF1C2028))
        }
        Background.NOITE -> drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF0B0F14)), endY = h))
        Background.GRADIENTE -> drawRect(Brush.linearGradient(listOf(Color(0xFF1B1F3A), Color(0xFF0B0F14), Color(0xFF10233A)), end = Offset(w, h)))
        Background.AURORA -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF060A16), Color(0xFF0B1030), Color(0xFF041A1E)), end = Offset(w * 0.4f, h)))
            blob(0.2f, 0.25f, 0.42f, Color(0xFF1C6BB0)); blob(0.75f, 0.2f, 0.36f, Color(0xFF7A3FD1))
            blob(0.7f, 0.85f, 0.42f, Color(0xFF17A28C)); blob(0.25f, 0.9f, 0.3f, Color(0xFFC5447A))
        }
        Background.OCEANO -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF020810), Color(0xFF06253A), Color(0xFF0C4F6B)), endY = h))
            blob(0.5f, 1.1f, 0.5f, Color(0xFF0A5F8A)); blob(0.8f, 0.1f, 0.28f, Color(0xFF3FC1D8))
        }
        Background.OCASO -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF120A1F), Color(0xFF3A1744), Color(0xFF6E2B3B)), endY = h))
            blob(0.5f, 0.75f, 0.4f, Color(0xFFFF8A3D)); blob(0.15f, 0.3f, 0.3f, Color(0xFFFF3D77)); blob(0.85f, 0.25f, 0.36f, Color(0xFF5B2A9C))
        }
        Background.IMAGEM, Background.HORIZONTE -> drawRect(Color.Black)
    }
}

/**
 * Fundo da tela: o bitmap do papel de parede, a cor do item focado como tingimento,
 * vinheta e escurecimento noturno. Sem o bitmap ainda (primeiro quadro), fundo escuro.
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val tint by animateColorAsState(glass.focusColor, tween(250), label = "tint")
    Canvas(modifier.fillMaxSize()) {
        val wp = glass.wallpaper
        if (wp == null) drawRect(TvColors.background)
        else drawImage(wp, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
        val fx = glass.config.fx
        if (glass.config.enabled && !glass.config.lite && tint.alpha > 0.01f && fx > 0f) {
            // Só a região central (não a tela inteira): o shader radial é caro por pixel na Mali
            val r = size.width * 0.45f
            val c = Offset(size.width * 0.5f, size.height * 0.6f)
            drawRect(
                Brush.radialGradient(listOf(tint.copy(alpha = 0.35f * fx * tint.alpha), Color.Transparent), center = c, radius = r),
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )
        }
        if (glass.darken > 0f) drawRect(Color.Black.copy(alpha = glass.darken))
    }
}

/**
 * Painel de vidro: recorta o papel de parede na forma, tinge, reflexo no topo e borda iluminada.
 * Com o estilo desligado não desenha nada (o Surface do TV Material cuida do fundo).
 * Modo leve: painel escuro translúcido, sem ver-através nem reflexos.
 */
fun Modifier.glass(shape: RoundedCornerShape = CardShape, strong: Boolean = false): Modifier = composed {
    val glass = LocalGlass.current
    if (!glass.config.enabled) return@composed this
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        // O recorte arredondado é feito pelo RenderNode (outline), barato na GPU; sem clipPath por quadro.
        .clip(shape)
        .onGloballyPositioned { val p = it.positionInRoot(); if (p != pos) pos = p }
        .drawWithCache {
            val cfg = glass.config
            val fx = cfg.fx
            val radius = CornerRadius(shape.topStart.toPx(size, this))
            val tintAlpha = ((if (strong) 0.26f else 0.2f) * (1f - cfg.transparency) + 0.03f).coerceIn(0f, 1f)
            val tint = Color.White.copy(alpha = tintAlpha)
            val lite = Color(0xFF10141E).copy(alpha = (0.95f - 0.45f * cfg.transparency).coerceIn(0.3f, 1f))
            val edge = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.55f * fx), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.02f), Color.White.copy(alpha = 0.3f * fx)),
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
            // Reflexo no topo: gradiente linear (shader barato), inclinado para parecer luz vindo da esquerda
            val specular = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.2f * fx), Color.White.copy(alpha = 0.04f * fx), Color.Transparent),
                start = Offset(0f, 0f), end = Offset(size.width * 0.35f, size.height * 0.75f),
            )
            val wp = glass.wallpaperBlur
            val screen = glass.screen
            val stroke = Stroke(1.5.dp.toPx())
            val inset = stroke.width / 2f
            onDrawBehind {
                // "pos" é lido AQUI (fase de desenho): mover o painel só invalida o desenho dele,
                // sem reconstruir o cache nem recompor. É o que mantém a rolagem barata.
                val p = pos
                if (cfg.lite || wp == null || screen == IntSize.Zero) {
                    drawRect(lite)
                } else {
                    // Só o recorte do papel de parede sob o painel (bitmap em meia resolução), não a tela inteira
                    val sx = wp.width.toFloat() / screen.width; val sy = wp.height.toFloat() / screen.height
                    val srcX = (p.x * sx).toInt(); val srcY = (p.y * sy).toInt()
                    val srcW = kotlin.math.ceil(size.width * sx).toInt() + 1; val srcH = kotlin.math.ceil(size.height * sy).toInt() + 1
                    if (srcX >= 0 && srcY >= 0 && srcX + srcW <= wp.width && srcY + srcH <= wp.height) {
                        drawImage(
                            wp, srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(srcW, srcH),
                            dstOffset = IntOffset.Zero, dstSize = IntSize(kotlin.math.ceil(size.width).toInt() + 1, kotlin.math.ceil(size.height).toInt() + 1),
                            filterQuality = FilterQuality.Low,
                        )
                    } else {
                        // Painel parcialmente fora da tela (rolagem/escala): caminho simples, a GPU recorta
                        translate(-p.x, -p.y) { drawImage(wp, dstSize = screen, filterQuality = FilterQuality.Low) }
                    }
                    drawRect(tint)
                    if (glass.darken > 0f) drawRect(Color.Black.copy(alpha = glass.darken))
                }
                if (!cfg.lite && fx > 0f) {
                    drawRect(specular)
                    // Borda iluminada: drawRoundRect tem caminho rápido no HWUI (sem tesselar path)
                    drawRoundRect(edge, topLeft = Offset(inset, inset), size = Size(size.width - stroke.width, size.height - stroke.width), cornerRadius = radius, style = stroke)
                }
            }
        }
}

/**
 * Luz varrendo o painel quando ele ganha foco (uma vez, 1,2 s) e brilho na cor do app por fora.
 * Só com animações ligadas e fx > 0.
 */
fun Modifier.focusSweep(focused: Boolean): Modifier = composed {
    val glass = LocalGlass.current
    val cfg = glass.config
    if (!cfg.enabled || cfg.lite || !cfg.animations || cfg.fx <= 0f) return@composed this
    val sweep = remember { Animatable(-1f) }
    LaunchedEffect(focused) {
        if (focused) { sweep.snapTo(-0.6f); sweep.animateTo(1.6f, tween(700)) } else sweep.snapTo(-1f)
    }
    drawWithCache {
        onDrawWithContent {
            drawContent()
            val t = sweep.value
            if (focused && t > -1f && t < 1.6f) {
                val x = size.width * t
                val band = Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.32f * cfg.fx), Color.Transparent),
                    start = Offset(x - size.width * 0.35f, 0f), end = Offset(x + size.width * 0.15f, size.height),
                )
                drawRect(band)   // o Surface (ou o clip do glass) já recorta na forma
            }
        }
    }
}

/** Brilho suave por fora do card focado, na cor do app. Vai no modifier EXTERNO (fora do clip). */
fun Modifier.focusGlow(focused: Boolean, color: Color): Modifier = composed {
    val glass = LocalGlass.current
    val cfg = glass.config
    if (!cfg.enabled || cfg.lite || cfg.fx <= 0f) return@composed this
    val amount = remember { Animatable(0f) }
    LaunchedEffect(focused) { amount.animateTo(if (focused) 1f else 0f, tween(if (cfg.animations) 220 else 0)) }
    drawWithCache {
        onDrawBehind {
            val a = amount.value
            if (a > 0.01f) {
                val c = if (color.alpha > 0f) color else Color.White
                // Pouco além do card (as linhas recortam o que passa de ~12 dp), concentrado embaixo como luz projetada.
                drawRect(
                    Brush.radialGradient(listOf(c.copy(alpha = 0.5f * a * cfg.fx), Color.Transparent), center = Offset(center.x, center.y + size.height * 0.15f), radius = size.width * 0.62f),
                    topLeft = Offset(-size.width * 0.12f, -size.height * 0.12f), size = Size(size.width * 1.24f, size.height * 1.3f),
                )
            }
        }
    }
}

/** Cor dominante do banner do app (média dos pixels mais saturados), em cache por pacote. */
private val appColors = HashMap<String, Color>()
fun appColor(app: AppEntry): Color = appColors.getOrPut(app.packageName) {
    val d = app.banner ?: return@getOrPut Color.White
    val bmp = runCatching { d.toBitmap(16, 9, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return@getOrPut Color.White
    var r = 0.0; var g = 0.0; var b = 0.0; var wsum = 0.0
    for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
        val p = bmp.getPixel(x, y)
        val pr = (p shr 16) and 0xFF; val pg = (p shr 8) and 0xFF; val pb = p and 0xFF
        val mx = maxOf(pr, pg, pb); val mn = minOf(pr, pg, pb)
        val wgt = (mx - mn + 8).toDouble() * ((p ushr 24) / 255.0)
        r += pr * wgt; g += pg * wgt; b += pb * wgt; wsum += wgt
    }
    if (wsum <= 0) Color.White else Color((r / wsum / 255).toFloat(), (g / wsum / 255).toFloat(), (b / wsum / 255).toFloat())
}
