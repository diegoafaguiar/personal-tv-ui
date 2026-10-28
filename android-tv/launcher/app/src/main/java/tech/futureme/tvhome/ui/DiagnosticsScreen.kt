package tech.futureme.tvhome.ui

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tech.futureme.tvhome.data.HomeContent
import tech.futureme.tvhome.data.TeamContent

/**
 * Diagnóstico: o que esta TV oferece ao launcher, em cartões de vidro por assunto (permissão,
 * apps e entradas, conteúdo publicado, TV aberta, seções). Para suporte, "Registrar no log"
 * grava tudo com a tag TvHome:  adb logcat -d -s TvHome
 */
@Composable
fun DiagnosticsScreen(content: HomeContent, team: TeamContent, appLabel: (String) -> String) {
    val d = content.diagnostics
    val groups: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "Permissões e erros" to buildList {
            add("Ler conteúdo dos apps (READ_TV_LISTINGS)" to if (content.hasPermission) "concedida" else "não concedida")
            if (d.error != null) add("Erro ao ler" to d.error)
        },
        "Apps e entradas" to listOf(
            "Apps de TV instalados" to "${content.apps.size}",
            "Entradas" to content.inputs.joinToString { it.label }.ifBlank { "nenhuma" },
        ),
        "Continuar assistindo, por app" to d.watchNextByApp.entries.sortedByDescending { it.value }.map { appLabel(it.key) to "${it.value}" }.ifEmpty { listOf("Nenhum app publicou" to "—") },
        "Sugestões (canais de prévia), por app" to d.channelsByApp.entries.sortedByDescending { it.value }.map { appLabel(it.key) to "${it.value} canais · ${d.programsByApp[it.key] ?: 0} itens" }.ifEmpty { listOf("Nenhum app publicou" to "—") },
        "TV aberta" to buildList {
            add("Canais visíveis" to "${d.tunerChannels.size}")
            add("Grade (agora e depois)" to if (d.epgSample.isEmpty()) "indisponível" else "${d.epgSample.size} canais com grade")
            add("Canais de esporte" to content.sportsChannels.joinToString { it.name }.ifBlank { "nenhum" })
        },
        "Seção do time" to listOf(
            "Vídeos" to "${team.videos.size}",
            "Itens dos apps citando o time" to "${team.related.size}",
            "Jogo e tabela (ESPN)" to if (team.info != null) "ok · ${team.info.position ?: "—"}º" else "sem dados",
        ) + team.errors.map { "Falha" to it },
        "Seções da home" to content.sections.map { (s, items) -> s.title to "${items.size}" } + ("Gêneros publicados" to d.genresSeen.sorted().joinToString(", ").ifBlank { "nenhum" }),
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("Diagnóstico", "O que esta TV e os apps oferecem ao launcher") }
        item {
            Row(Modifier.padding(sidePadding())) {
                ActionButton("Registrar no log (suporte)", {
                    groups.forEach { (t, rows) -> Log.i("TvHome", "— $t —"); rows.forEach { (k, v) -> Log.i("TvHome", "  $k: $v") } }
                    d.tunerChannels.forEach { Log.i("TvHome", "  canal: $it") }
                    d.epgSample.forEach { Log.i("TvHome", "  grade: $it") }
                })
            }
        }
        items(groups, key = { it.first }) { (title, rows) ->
            var focused by remember { mutableStateOf(false) }
            val accent = LocalLook.current.accentColor
            Column(Modifier.padding(sidePadding()).fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusable()
                .border(if (focused) 2.dp else 0.dp, if (focused) accent else Color.Transparent, RoundedCornerShape(18.dp)).glass(RoundedCornerShape(18.dp), strong = true).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
                for ((k, v) in rows) Row {
                    Text(k, fontSize = 14.sp, color = TvColors.text.copy(alpha = 0.78f), modifier = Modifier.width(360.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(v, fontSize = 14.sp, color = TvColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
