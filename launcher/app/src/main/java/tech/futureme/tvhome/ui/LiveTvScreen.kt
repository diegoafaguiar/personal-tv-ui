package tech.futureme.tvhome.ui

import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tech.futureme.tvhome.data.TunerChannel

/**
 * TV aberta dentro da home: o TvView é o mesmo componente que o app de TV usa
 * para pedir vídeo ao serviço do sintonizador (com.tcl.tvinput). Não depende
 * do app da TCL aceitar ser aberto por outro app.
 * Cima/baixo trocam de canal; números digitam; Voltar sai.
 */
@Composable
fun LiveTvScreen(
    channels: List<TunerChannel>,
    initial: TunerChannel,
    onFailed: (TunerChannel, String) -> Unit,
    onChannelChanged: (TunerChannel) -> Unit,
) {
    var current by remember { mutableStateOf(initial) }
    var message by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(true) }
    var typed by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    var view by remember { mutableStateOf<TvView?>(null) }

    fun tune(ch: TunerChannel) {
        current = ch; showInfo = true; message = null
        onChannelChanged(ch)
        view?.tune(ch.inputId, TvContract.buildChannelUri(ch.id))
    }
    fun step(delta: Int) {
        if (channels.isEmpty()) return
        val i = channels.indexOfFirst { it.id == current.id }.let { if (it < 0) 0 else it }
        tune(channels[(i + delta + channels.size) % channels.size])
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(showInfo, current.id) { if (showInfo) { delay(4000); showInfo = false } }
    LaunchedEffect(typed) {
        if (typed.isNotEmpty()) {
            delay(1500)
            val target = channels.firstOrNull { it.number == typed || it.number == "$typed.1" || it.number.substringBefore('.') == typed }
            if (target != null) tune(target) else message = "Canal $typed não encontrado"
            typed = ""
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (ev.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { step(+1); true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_INFO -> { showInfo = !showInfo; true }
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { typed += (ev.nativeKeyEvent.keyCode - KeyEvent.KEYCODE_0).toString(); true }
                else -> false
            }
        },
    ) {
        AndroidView(
            factory = { ctx ->
                TvView(ctx).apply {
                    setCallback(object : TvView.TvInputCallback() {
                        override fun onConnectionFailed(inputId: String) { onFailed(current, "Sem conexão com o sintonizador ($inputId)") }
                        override fun onDisconnected(inputId: String) { message = "Sintonizador desconectado" }
                        override fun onVideoUnavailable(inputId: String, reason: Int) {
                            message = when (reason) {
                                TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING -> "Sintonizando…"
                                TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL -> "Sinal fraco"
                                TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> "Carregando…"
                                TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY -> "Só áudio"
                                else -> "Vídeo indisponível ($reason)"
                            }
                        }
                        override fun onVideoAvailable(inputId: String) { message = null }
                        override fun onContentBlocked(inputId: String, rating: android.media.tv.TvContentRating) { message = "Bloqueado pelo controle parental" }
                    })
                    setStreamVolume(1f)
                    view = this
                    tune(initial.inputId, TvContract.buildChannelUri(initial.id))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        DisposableEffect(Unit) { onDispose { view?.reset(); view = null } }

        if (showInfo || message != null || typed.isNotEmpty()) {
            Column(Modifier.align(Alignment.BottomStart).padding(48.dp)) {
                if (typed.isNotEmpty()) Text(typed, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${current.number}  ${current.name}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                if (current.nowTitle != null) Text("Agora: ${current.nowTitle}", fontSize = 18.sp, color = Color(0xCCFFFFFF))
                if (current.nextTitle != null) Text("Depois: ${current.nextTitle}", fontSize = 15.sp, color = Color(0x99FFFFFF))
                if (message != null) Text(message!!, fontSize = 16.sp, color = Color(0xFFFFC107))
                Text("▲▼ canal · números · OK info · Voltar sai", fontSize = 12.sp, color = Color(0x80FFFFFF))
            }
        }
    }
}

@Suppress("unused")
private fun channelUri(id: Long): Uri = TvContract.buildChannelUri(id)
