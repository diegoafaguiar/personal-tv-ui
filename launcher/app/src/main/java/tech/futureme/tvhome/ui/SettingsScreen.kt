package tech.futureme.tvhome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tech.futureme.tvhome.data.AppEntry
import tech.futureme.tvhome.data.Background
import tech.futureme.tvhome.data.Profile
import tech.futureme.tvhome.data.Profiles
import tech.futureme.tvhome.data.QuickAction
import tech.futureme.tvhome.data.Section
import tech.futureme.tvhome.data.Settings

@Composable
fun SettingsScreen(
    apps: List<AppEntry>,
    settings: Settings,
    onToggleDock: (String) -> Unit,
    onMoveDock: (String, Int) -> Unit,
    onToggleHidden: (String) -> Unit,
    onToggleSection: (Section) -> Unit,
    onSetLiveTv: (String) -> Unit,
    onSetBackground: (Background) -> Unit,
    onToggleDensity: () -> Unit,
    onToggleQuick: (QuickAction) -> Unit,
    onToggleRates: () -> Unit,
    onToggleInputs: () -> Unit,
    onToggleWeather: () -> Unit,
    onToggleNight: () -> Unit,
    onClearRecents: () -> Unit,
    onToggleFast: () -> Unit,
    onToggleTeam: () -> Unit,
    onToggleStatus: () -> Unit,
    onToggleHeroCompact: () -> Unit,
    onMoveOrder: (String, Int) -> Unit,
    onChangePin: (Profile) -> Unit,
    onToggleGlass: () -> Unit,
    onAdjustTransparency: (Int) -> Unit,
    onAdjustFx: (Int) -> Unit,
    onAdjustBlur: (Int) -> Unit,
    onToggleAnimations: () -> Unit,
    onToggleLite: () -> Unit,
    onOpenTvSettings: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp)) {
        item { RowTitle("Ajustes da home", "Voltar sai desta tela. As mudanças valem na hora.") }
        item { SettingRow("Configurações da TV (Android TV)", "rede, som, imagem, apps, sistema") { onOpenTvSettings() } }

        item { RowTitle("Perfis", "Senha dos perfis adultos (padrão 1234). O perfil infantil não tem senha.") }
        items(Profiles.ALL.filter { it.requiresPin }, key = { "pin-" + it.id }) { p ->
            SettingRow("Senha de ${p.name}", if (settings.pins.containsKey(p.id)) "personalizada" else "padrão") { onChangePin(p) }
        }

        item { RowTitle("Vidro", "Painéis translúcidos sobre o papel de parede. OK liga/desliga; ◀ ▶ ajustam.") }
        item { SettingRow("Estilo vidro", if (settings.glass.enabled) "ligado" else "desligado (cards escuros)") { onToggleGlass() } }
        item { AdjustRow("Transparência", "${(settings.glass.transparency * 100).toInt()}%", onAdjustTransparency) }
        item { AdjustRow("Intensidade dos efeitos (reflexos, borda, brilho)", "${(settings.glass.fx * 100).toInt()}%", onAdjustFx) }
        item { AdjustRow("Desfoque de \"Minha imagem\"", listOf("nítido", "leve", "médio", "forte")[settings.glass.blur.coerceIn(0, 3)], onAdjustBlur) }
        item { SettingRow("Animações (escala e varredura de luz no foco)", if (settings.glass.animations) "ligadas" else "desligadas") { onToggleAnimations() } }
        item { SettingRow("Modo leve (sem ver-através nem reflexos; para GPU fraca)", if (settings.glass.lite) "ligado" else "desligado") { onToggleLite() } }

        item { RowTitle("Aparência") }
        item { SettingRow("Densidade dos cards", if (settings.density == "normal") "normal (~4 por linha)" else "compacta (~6 por linha)") { onToggleDensity() } }
        item { SettingRow("Hero sempre compacto (uma linha)", if (settings.heroCompact) "ligado" else "desligado") { onToggleHeroCompact() } }
        item { SettingRow("Estado da TV no hero (rede, Bluetooth, memória)", if (settings.showStatus) "ligado" else "desligado") { onToggleStatus() } }
        item { SettingRow("Seção ${settings.teamName}", if (settings.showTeam) "ligada" else "desligada") { onToggleTeam() } }
        item { SettingRow("Cotações no topo (dólar e euro)", if (settings.showRates) "ligado" else "desligado") { onToggleRates() } }
        item { SettingRow("Clima no topo (Recreio dos Bandeirantes)", if (settings.showWeather) "ligado" else "desligado") { onToggleWeather() } }
        item { SettingRow("Modo noturno automático (escurece das 21h às 6h)", if (settings.nightMode) "ligado" else "desligado") { onToggleNight() } }
        item { SettingRow("Ícones de HDMI no menu", if (settings.showInputs) "ligado" else "desligado") { onToggleInputs() } }
        item { SettingRow("Linha \"Canais pela internet\" (TCL Channel+)", if (settings.showFast) "ligada" else "desligada") { onToggleFast() } }
        item { SettingRow("Limpar \"Abertos recentemente\"", "${settings.recents.size} itens") { onClearRecents() } }
        item { RowTitle("Papel de parede", "Aurora, Oceano e Ocaso valorizam o vidro. Para \"Minha imagem\": scripts/58-launcher-wallpaper.sh foto.jpg") }
        items(Background.entries, key = { "bg-" + it.name }) { bg ->
            SettingRow(bg.label, if (settings.background == bg) "● em uso" else "") { onSetBackground(bg) }
        }

        item { RowTitle("Ícones do menu nativo") }
        items(QuickAction.entries, key = { "quick-" + it.name }) { q ->
            SettingRow(q.label, if (q in settings.quick) "visível" else "oculto") { onToggleQuick(q) }
        }

        item { RowTitle("Ordem dos apps nas seções", "Vale para continuar assistindo, filmes, séries e sugestões") }
        items(apps.sortedBy { settings.appRank(it.packageName) }, key = { "order-" + it.packageName }) { app ->
            val pos = settings.appOrder.indexOf(app.packageName)
            Row(
                Modifier.fillMaxWidth().padding(sidePadding()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingRow(app.label, if (pos >= 0) "${pos + 1}º" else "sem posição", modifier = Modifier.width(520.dp)) { onMoveOrder(app.packageName, 0) }
                PillSmall("▲") { onMoveOrder(app.packageName, -1) }
                PillSmall("▼") { onMoveOrder(app.packageName, +1) }
            }
        }

        item { RowTitle("Seções") }
        items(Section.entries, key = { "sec-" + it.name }) { section ->
            val off = section.name in settings.disabledSections
            SettingRow(section.title, if (off) "oculta" else "visível") { onToggleSection(section) }
        }

        item { RowTitle("Dock", "OK adiciona/remove; as setas mudam a ordem") }
        items(apps, key = { "dock-" + it.packageName }) { app ->
            val inDock = app.packageName in settings.dock
            val pos = settings.dock.indexOf(app.packageName)
            Row(
                Modifier.fillMaxWidth().padding(sidePadding()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingRow(app.label, if (inDock) "no dock (${pos + 1})" else "fora", modifier = Modifier.width(520.dp)) { onToggleDock(app.packageName) }
                if (inDock) {
                    PillSmall("◀") { onMoveDock(app.packageName, -1) }
                    PillSmall("▶") { onMoveDock(app.packageName, +1) }
                }
            }
        }

        item { RowTitle("Apps ocultos", "Somem do dock e de \"Todos os apps\"") }
        items(apps, key = { "hide-" + it.packageName }) { app ->
            val hidden = app.packageName in settings.hidden
            SettingRow(app.label, if (hidden) "oculto" else "visível") { onToggleHidden(app.packageName) }
        }

        item { RowTitle("App de TV ao vivo", "Quem abre quando você escolhe o sintonizador (atual: ${settings.liveTvPackage})") }
        items(apps.filter { it.packageName.contains("tv", ignoreCase = true) }, key = { "live-" + it.packageName }) { app ->
            SettingRow(app.label, app.packageName) { onSetLiveTv(app.packageName) }
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth().padding(sidePadding()).padding(vertical = 4.dp),
    onClick: () -> Unit,
) {
    FocusSurface(onClick = onClick, modifier = modifier.height(52.dp)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 18.sp, color = TvColors.text)
            Text(value, fontSize = 15.sp, color = TvColors.textDim)
        }
    }
}

/** Linha com valor e botões ◀ ▶ (OK na linha também avança). */
@Composable
private fun AdjustRow(label: String, value: String, onAdjust: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(sidePadding()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingRow(label, value, modifier = Modifier.width(520.dp)) { onAdjust(+1) }
        PillSmall("◀") { onAdjust(-1) }
        PillSmall("▶") { onAdjust(+1) }
    }
}

@Composable
private fun PillSmall(label: String, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = Modifier.width(52.dp).height(52.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 18.sp, color = TvColors.text)
        }
    }
}
