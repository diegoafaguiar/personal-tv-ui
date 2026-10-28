package tech.futureme.tvhome.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.futureme.tvhome.data.Prefs
import tech.futureme.tvhome.remote.RemoteServer

/**
 * Ajustes › Sistema › Controle pelo celular: QR com o endereço da TV e o código de acesso.
 * Desligar fecha o servidor; "Novo código" invalida os celulares que já leram o QR.
 */
@Composable
fun RemoteScreen(prefs: Prefs, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var port by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { token = prefs.remoteToken() }
    // O servidor sobe em segundo plano ao ligar: espera a porta aparecer
    LaunchedEffect(enabled) { repeat(20) { port = RemoteServer.instance?.port ?: 0; if (port > 0 || !enabled) return@LaunchedEffect; delay(250) } }
    val ip = remember(enabled, port) { RemoteServer.localIp() }
    val url = if (ip != null && port > 0 && token != null) "http://$ip:$port/?t=$token" else null
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().padding(start = 24.dp, end = LocalDims.current.side, top = 24.dp, bottom = 24.dp)) {
        Row(Modifier.glass(RoundedCornerShape(24.dp)).padding(28.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                val qr = remember(url) { url?.let { qrBitmap(it, 520) } }
                when {
                    !enabled -> Text("Desligado", fontSize = 18.sp, color = TvColors.textDim)
                    qr != null -> Image(qr, contentDescription = "QR code do controle", modifier = Modifier.size(260.dp))
                    ip == null -> Text("Sem rede", fontSize = 18.sp, color = TvColors.textDim)
                    else -> Text("Preparando…", fontSize = 18.sp, color = TvColors.textDim)
                }
            }
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Controle pelo celular", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
                Text("1. Deixe o celular no mesmo Wi-Fi da TV.", fontSize = 15.sp, color = TvColors.text)
                Text("2. Aponte a câmera do celular para o código.", fontSize = 15.sp, color = TvColors.text)
                Text("3. A página que abrir é o controle: setas, apps, busca, estilos e foto de fundo.", fontSize = 15.sp, color = TvColors.text)
                if (url != null && enabled) Text(url.substringBefore("?"), fontSize = 13.sp, color = TvColors.textDim, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Dentro de outros apps (Netflix, YouTube…) o celular faz Início, volume e abre apps; para navegar dentro deles, use o controle da TV ou o app Google TV.",
                    fontSize = 12.sp, color = TvColors.textDim, modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(if (enabled) "Desligar" else "Ligar", { scope.launch { prefs.setRemoteEnabled(!enabled) } }, Modifier.width(160.dp).focusRequester(first))
                    if (enabled) ActionButton("Novo código", { scope.launch { token = prefs.newRemoteToken() } }, Modifier.width(180.dp))
                }
            }
        }
    }
}
