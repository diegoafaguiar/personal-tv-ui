package io.github.diegoafaguiar.sintonia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.diegoafaguiar.sintonia.HomeViewModel
import io.github.diegoafaguiar.sintonia.data.NewsItem
import io.github.diegoafaguiar.sintonia.data.RatesRepository
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BR = Locale("pt", "BR")

/**
 * Câmbio: dólar e euro com compra, venda, máxima e mínima do dia, gráfico dos últimos 30 dias
 * (AwesomeAPI) e notícias de câmbio (Google Notícias). Aberta pelos chips de cotação do topo.
 */
@Composable
fun RatesScreen(rates: List<RatesRepository.Rate>, detail: HomeViewModel.RatesDetail, onOpenNews: (NewsItem) -> Unit) {
    val updated = rates.maxOfOrNull { it.atMs }?.takeIf { it > 0 }?.let { "atualizado às " + SimpleDateFormat("HH:mm", BR).format(Date(it)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp)) {
        item { PageHeader("Câmbio", listOfNotNull("Dólar e euro em reais", updated).joinToString(" · ")) }
        item {
            Row(Modifier.padding(sidePadding()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (r in rates.sortedBy { if (it.code == "USD") 0 else 1 }) {
                    RateCard(r, detail.history[r.code].orEmpty(), detail.loading, Modifier.weight(1f))
                }
            }
            if (rates.isEmpty()) EmptyState("Cotações indisponíveis", "Sem conexão com a AwesomeAPI agora. A página tenta de novo quando você voltar a ela.")
        }
        item { RowTitle("Notícias sobre câmbio", if (detail.loading && detail.news.isEmpty()) "carregando…" else "Google Notícias") }
        item {
            if (detail.news.isEmpty() && !detail.loading) EmptyState("Nenhuma notícia agora", "As notícias voltam quando houver conexão.")
            else LazyRow(Modifier.rememberFocus(), contentPadding = rowPadding(), horizontalArrangement = Arrangement.spacedBy(LocalDims.current.gap)) {
                items(detail.news, key = { "fx-" + it.link.hashCode() }) { NewsCard(it) { onOpenNews(it) } }
            }
        }
    }
}

@Composable
private fun RateCard(r: RatesRepository.Rate, history: List<RatesRepository.Point>, loading: Boolean, modifier: Modifier = Modifier) {
    val money = remember { NumberFormat.getCurrencyInstance(BR) }
    val four = remember { NumberFormat.getNumberInstance(BR).apply { minimumFractionDigits = 4; maximumFractionDigits = 4 } }
    // Focável (sem ação): o controle passa pelos cards e a página rola até as notícias
    FocusSurface(onClick = {}, shape = RoundedCornerShape(20.dp), focusScale = 1.02f, modifier = modifier.height(300.dp)) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TvColors.textDim, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(money.format(r.bid), fontSize = 40.sp, fontWeight = FontWeight.Light, color = TvColors.text)
                Text(
                    "${if (r.pctChange > 0) "▲" else if (r.pctChange < 0) "▼" else "•"} ${String.format(BR, "%.2f%%", kotlin.math.abs(r.pctChange))} hoje",
                    fontSize = 16.sp, fontWeight = FontWeight.Medium, color = changeColor(r.pctChange), modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Text(
                "Compra ${four.format(r.bid)} · Venda ${four.format(r.ask)} · Máx ${four.format(r.high)} · Mín ${four.format(r.low)}",
                fontSize = 13.sp, color = TvColors.textDim, maxLines = 1,
            )
            if (history.size >= 2) {
                val first = history.first().bid; val last = history.last().bid
                val pct = (last - first) / first * 100
                Text(
                    "30 dias: ${if (pct >= 0) "+" else "−"}${String.format(BR, "%.2f%%", kotlin.math.abs(pct))} · mín ${money.format(history.minOf { it.bid })} · máx ${money.format(history.maxOf { it.bid })}",
                    fontSize = 13.sp, color = TvColors.text, modifier = Modifier.padding(top = 6.dp),
                )
                Sparkline(history, changeColor(pct), Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp))
            } else {
                Text(if (loading) "Carregando o histórico…" else "Histórico indisponível", fontSize = 13.sp, color = TvColors.textDim, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Linha do preço no período; ponto no último valor. Desenho simples (um path), barato na GPU. */
@Composable
private fun Sparkline(points: List<RatesRepository.Point>, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val min = points.minOf { it.bid }; val max = points.maxOf { it.bid }
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val t0 = points.first().atMs; val tSpan = (points.last().atMs - t0).coerceAtLeast(1L)
        fun at(p: RatesRepository.Point) = Offset(
            ((p.atMs - t0).toFloat() / tSpan) * size.width,
            (size.height - ((p.bid - min) / span).toFloat() * size.height).coerceIn(2f, size.height - 2f),
        )
        // Linha de base (mínimo do período)
        drawLine(TvColors.textDim.copy(alpha = 0.25f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
        val path = Path()
        points.forEachIndexed { i, p -> val o = at(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 4.dp.toPx(), at(points.last()))
    }
}
