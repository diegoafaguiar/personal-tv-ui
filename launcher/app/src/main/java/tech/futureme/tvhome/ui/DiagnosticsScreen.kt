package tech.futureme.tvhome.ui

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tech.futureme.tvhome.data.HomeContent
import tech.futureme.tvhome.data.TeamContent

/**
 * Mostra o que cada app publica de fato nesta TV. É com isso que ajustamos
 * as regras do Classifier. "Enviar ao logcat" imprime tudo com a tag TvHome:
 *   adb logcat -d -s TvHome
 */
@Composable
fun DiagnosticsScreen(content: HomeContent, team: TeamContent, appLabel: (String) -> String) {
    val d = content.diagnostics
    val lines = buildList {
        add("— Seção do time —")
        add("  vídeos (YouTube RSS): ${team.videos.size}   relacionados nos apps: ${team.related.size}   ESPN: ${if (team.info != null) "ok (próximo: ${team.info.next?.opponent ?: "—"}, posição: ${team.info.position ?: "—"})" else "sem dados"}")
        team.errors.forEach { add("  erro: $it") }
        add("Permissão READ_TV_LISTINGS: ${if (content.hasPermission) "concedida" else "NÃO concedida (adb shell pm grant tech.futureme.tvhome android.permission.READ_TV_LISTINGS)"}")
        if (d.error != null) add("Erro: ${d.error}")
        add("Apps instalados (Leanback): ${content.apps.size}   Entradas: ${content.inputs.joinToString { it.label }}")
        add("— Watch Next por app —")
        if (d.watchNextByApp.isEmpty()) add("  (nenhum)")
        d.watchNextByApp.entries.sortedByDescending { it.value }.forEach { add("  ${appLabel(it.key)} (${it.key}): ${it.value}") }
        add("— Canais de prévia por app —")
        if (d.channelsByApp.isEmpty()) add("  (nenhum)")
        d.channelsByApp.entries.sortedByDescending { it.value }.forEach { add("  ${appLabel(it.key)}: ${it.value} canais, ${d.programsByApp[it.key] ?: 0} programas") }
        add("— Por tipo —")
        d.byType.entries.sortedByDescending { it.value }.forEach { add("  ${it.key}: ${it.value}") }
        add("— Gêneros vistos —")
        add("  " + (d.genresSeen.sorted().joinToString(", ").ifBlank { "(nenhum)" }))
        add("— Canais do sintonizador (TV aberta) visíveis ao launcher —")
        if (d.tunerChannels.isEmpty()) add("  (nenhum: o app de TV ao vivo não gravou canais no TvContract, ou a busca de canais não rodou)")
        d.tunerChannels.forEach { add("  $it") }
        add("— Grade (agora / depois) —")
        if (d.epgSample.isEmpty()) add("  (nenhuma)")
        d.epgSample.forEach { add(it) }
        add("— Canais de esporte detectados (seção Futebol) —")
        content.sportsChannels.forEach { add("  ${it.number} ${it.name}") }
        add("— Seções —")
        content.sections.forEach { (s, items) -> add("  ${s.title}: ${items.size}") }
        add("— Amostra de títulos por seção (para calibrar o Classifier) —")
        content.sections.forEach { (s, items) ->
            items.take(8).forEach { add("  [${s.name}] ${it.title} · ${appLabel(it.packageName)} · tipo ${it.type} · ${it.genres.joinToString("/")}") }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp)) {
        item { RowTitle("Diagnóstico", "O que os apps publicam nesta TV. Voltar sai.") }
        item {
            LazyRow(contentPadding = rowPadding()) {
                item { PillCard("Enviar ao logcat", { lines.forEach { Log.i("TvHome", it) } }) }
            }
        }
        items(lines.size) { i ->
            Text(lines[i], fontSize = 15.sp, color = TvColors.text, modifier = Modifier.padding(rowPadding()).padding(vertical = 2.dp))
        }
    }
}
