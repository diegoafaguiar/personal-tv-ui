package tech.futureme.tvhome.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tech.futureme.tvhome.data.Profile
import tech.futureme.tvhome.data.ProfileKind

/** Tela de abertura: quem está assistindo? */
@Composable
fun ProfilePicker(profiles: List<Profile>, onPick: (Profile) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Quem está assistindo?", fontSize = 30.sp, fontWeight = FontWeight.Light, color = TvColors.text)
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            profiles.forEachIndexed { i, p ->
                ProfileCard(p, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) { onPick(p) }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Os perfis com cadeado pedem senha", fontSize = 13.sp, color = TvColors.textDim)
    }
}

@Composable
fun ProfileCard(p: Profile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.width(220.dp).height(240.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(104.dp).background(Brush.linearGradient(listOf(Color(p.color), Color(p.color).copy(alpha = 0.55f))), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(p.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TvColors.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.requiresPin) Icon(Icons.Rounded.Lock, contentDescription = "senha", tint = TvColors.textDim, modifier = Modifier.size(14.dp))
                    Text(if (p.kind == ProfileKind.KIDS) "Infantil" else "Adulto", fontSize = 13.sp, color = TvColors.textDim)
                }
            }
        }
    }
}

/**
 * Senha numérica: teclas numéricas do controle ou o teclado na tela.
 * "expected" nulo = modo de definir senha nova (devolve o que foi digitado).
 */
@Composable
fun PinPad(title: String, subtitle: String, expected: String?, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    LaunchedEffect(typed) {
        if (typed.length == 4) {
            if (expected == null || typed == expected) { delay(150); onDone(typed) }
            else { error = "Senha incorreta"; delay(900); typed = ""; error = null }
        }
    }
    fun press(d: Char) { if (typed.length < 4) { typed += d; error = null } }

    Column(
        Modifier.fillMaxSize().padding(48.dp).onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val code = ev.nativeKeyEvent.keyCode
            when {
                code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { press('0' + (code - KeyEvent.KEYCODE_0)); true }
                code == KeyEvent.KEYCODE_DEL -> { typed = typed.dropLast(1); true }
                else -> false
            }
        },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Light, color = TvColors.text)
        Text(subtitle, fontSize = 14.sp, color = TvColors.textDim)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(Modifier.size(18.dp).background(if (i < typed.length) TvColors.text else Color(0x33FFFFFF), CircleShape))
            }
        }
        Text(error ?: " ", fontSize = 14.sp, color = Color(0xFFE57373), modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(10.dp))
        val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('⌫', '0', '✕'))
        rows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                row.forEachIndexed { c, ch ->
                    val mod = if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier
                    FocusSurface(
                        onClick = { when (ch) { '⌫' -> typed = typed.dropLast(1); '✕' -> onCancel(); else -> press(ch) } },
                        modifier = mod.size(64.dp).semantics { contentDescription = when (ch) { '⌫' -> "Apagar"; '✕' -> "Cancelar"; else -> ch.toString() } },
                        shape = RoundedCornerShape(50),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(ch.toString(), fontSize = 22.sp, color = TvColors.text) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Dica: os números do controle também funcionam · Voltar cancela", fontSize = 12.sp, color = TvColors.textDim)
    }
}
