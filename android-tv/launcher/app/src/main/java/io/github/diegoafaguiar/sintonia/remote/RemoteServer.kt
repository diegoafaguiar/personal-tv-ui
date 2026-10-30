package io.github.diegoafaguiar.sintonia.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import io.github.diegoafaguiar.sintonia.data.Accent
import io.github.diegoafaguiar.sintonia.data.AppsRepository
import io.github.diegoafaguiar.sintonia.data.Background
import io.github.diegoafaguiar.sintonia.data.BgRotation
import io.github.diegoafaguiar.sintonia.data.FontChoice
import io.github.diegoafaguiar.sintonia.data.Prefs
import io.github.diegoafaguiar.sintonia.data.StylePreset
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Controle pelo celular: um servidor HTTP mínimo dentro do launcher, só na rede local.
 *
 * - GET  /                 a página do controle (assets/remote/index.html)
 * - GET  /api/state        apps, estilos, fundos, cores e o que está em uso · /api/ping (só "a home está na frente?")
 * - GET  /api/icon/<pkg>   ícone do app em PNG
 * - POST /api/key          {"key":"up|down|left|right|ok|back|home|vol_up|vol_down|mute"}
 * - POST /api/open         {"pkg":"com.netflix.ninja"} · /api/search {"q":"…"} · /api/live
 * - POST /api/style        {"name":"VIDRO"} · /api/background {"name":"NOITE"} · /api/accent {"name":"CIANO"}
 * - POST /api/dock         {"pkgs":[…]} (favoritos em ordem)
 * - POST /api/wallpaper    corpo = JPEG/PNG da foto (vira "Minha imagem")
 * - POST /api/font         {"name":"CONDENSADA"}
 * - GET  /api/settings     backup dos ajustes (JSON para baixar) · POST /api/settings restaura
 *
 * Toda chamada /api precisa do código do QR (cabeçalho X-Token ou ?t=). Só aceita conexões de
 * endereços da rede local (10.x, 172.16–31.x, 192.168.x, link-local). Setas e OK chegam
 * na janela do próprio launcher; dentro de outro app não chegam, e
 * a página avisa. Volume usa o AudioManager e funciona em qualquer tela.
 * As teclas não são injetadas (INJECT_EVENTS é só do sistema): a Activity as despacha na
 * própria janela, pelo RemoteBus.
 */
class RemoteServer(private val context: Context) {
    private val prefs = Prefs(context)
    private val apps = AppsRepository(context)
    private val pool = Executors.newFixedThreadPool(4)
    private val icons = ConcurrentHashMap<String, ByteArray>()
    @Volatile private var server: ServerSocket? = null
    @Volatile var port: Int = 0; private set

    fun start() {
        if (server != null) return
        val s = (PORTS.firstNotNullOfOrNull { p -> runCatching { ServerSocket(p) }.getOrNull() }) ?: run {
            Log.w("TvHome", "Controle pelo celular: nenhuma porta livre em $PORTS"); return
        }
        server = s; port = s.localPort
        Thread({
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(client) }.onFailure { Log.w("TvHome", "Controle: $it") }; runCatching { client.close() } }
            }
        }, "tvhome-remote").apply { isDaemon = true }.start()
        Log.i("TvHome", "Controle pelo celular na porta $port")
    }

    fun stop() { runCatching { server?.close() }; server = null; port = 0 }

    // ---------------------------------------------------------------- HTTP

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: ByteArray)

    private fun handle(socket: Socket) {
        socket.soTimeout = 15_000
        if (!isLocal(socket.inetAddress)) return respond(socket.getOutputStream(), 403, "text/plain", "rede local apenas".toByteArray())
        val req = read(socket.getInputStream()) ?: return
        val out = socket.getOutputStream()
        if (req.path == "/" || req.path == "/index.html") {
            val html = context.assets.open("remote/index.html").use { it.readBytes() }
            return respond(out, 200, "text/html; charset=utf-8", html)
        }
        if (!req.path.startsWith("/api/")) return respond(out, 404, "text/plain", "não encontrado".toByteArray())
        val token = runBlocking { prefs.remoteToken() }
        val given = req.headers["x-token"] ?: req.query["t"]
        if (given != token) return json(out, 401, JSONObject().put("ok", false).put("error", "Código inválido: leia o QR de novo na TV."))
        try {
            route(req, out)
        } catch (e: Exception) {
            Log.w("TvHome", "Controle ${req.path}: $e")
            json(out, 500, JSONObject().put("ok", false).put("error", "Não deu certo: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    private fun route(req: Request, out: OutputStream) {
        val p = req.path
        if (req.method == "GET" && p == "/api/state") return json(out, 200, state())
        // Leve, para o indicador da página (o estado completo lista os apps: caro numa TV fraca)
        if (req.method == "GET" && p == "/api/ping") return json(out, 200, ok().put("foreground", RemoteBus.foreground))
        if (req.method == "GET" && p.startsWith("/api/icon/")) {
            val png = icon(p.removePrefix("/api/icon/")) ?: return respond(out, 404, "text/plain", ByteArray(0))
            return respond(out, 200, "image/png", png, cache = true)
        }
        if (req.method == "GET" && p == "/api/settings") {
            val body = runBlocking { prefs.exportJson() }.toByteArray()
            return respond(out, 200, "application/json; charset=utf-8", body, attachment = "sintonia-ajustes.json")
        }
        if (req.method != "POST") return respond(out, 405, "text/plain", ByteArray(0))
        if (p == "/api/wallpaper") return json(out, 200, wallpaper(req.body))
        if (p == "/api/settings") {
            val n = runBlocking { prefs.importJson(String(req.body)) }
            return json(out, if (n > 0) 200 else 400, if (n > 0) ok().put("applied", n) else err("Arquivo não é um backup do Sintonia"))
        }
        val b = runCatching { JSONObject(String(req.body)) }.getOrDefault(JSONObject())
        val result: JSONObject = when (p) {
            "/api/key" -> key(b.optString("key"))
            "/api/open" -> { RemoteBus.send(RemoteCommand.OpenApp(b.optString("pkg"))); ok() }
            "/api/search" -> { RemoteBus.send(RemoteCommand.Search(b.optString("q").take(80))); ok() }
            "/api/live" -> { RemoteBus.send(RemoteCommand.LiveTv); ok() }
            "/api/style" -> {
                val st = StylePreset.entries.firstOrNull { it.name == b.optString("name") } ?: return json(out, 400, err("Estilo desconhecido"))
                runBlocking { prefs.applyStyle(st) }; ok()
            }
            "/api/background" -> {
                val bg = Background.entries.firstOrNull { it.name == b.optString("name") } ?: return json(out, 400, err("Fundo desconhecido"))
                runBlocking { prefs.setBackground(bg) }; ok()
            }
            "/api/rotation" -> {
                val r = BgRotation.entries.firstOrNull { it.name == b.optString("name") } ?: return json(out, 400, err("Opção desconhecida"))
                runBlocking { prefs.setBgRotation(r) }; ok()
            }
            "/api/accent" -> {
                val a = Accent.entries.firstOrNull { it.name == b.optString("name") } ?: return json(out, 400, err("Cor desconhecida"))
                runBlocking { prefs.setAccent(a) }; ok()
            }
            "/api/font" -> {
                val f = FontChoice.entries.firstOrNull { it.name == b.optString("name") } ?: return json(out, 400, err("Fonte desconhecida"))
                runBlocking { prefs.setFont(f) }; ok()
            }
            "/api/dock" -> {
                val arr = b.optJSONArray("pkgs") ?: JSONArray()
                runBlocking { prefs.setDock((0 until arr.length()).map { arr.getString(it) }) }; ok()
            }
            else -> return respond(out, 404, "text/plain", ByteArray(0))
        }
        json(out, 200, result)
    }

    private fun ok() = JSONObject().put("ok", true)
    private fun err(msg: String) = JSONObject().put("ok", false).put("error", msg)

    // ---------------------------------------------------------------- ações

    private fun key(name: String): JSONObject {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (name) {
            "vol_up" -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return ok() }
            "vol_down" -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return ok() }
            "mute" -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); return ok() }
            "home" -> { RemoteBus.send(RemoteCommand.Home); return ok() }
        }
        val code = when (name) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP; "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT; "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "ok" -> KeyEvent.KEYCODE_DPAD_CENTER; "back" -> KeyEvent.KEYCODE_BACK
            else -> return err("Tecla desconhecida: $name")
        }
        if (!RemoteBus.foreground) return err("Dentro de outro app as setas não chegam. Use Início, ou o controle da TV / app Google TV.")
        // Entregue pela própria Activity (dispatchKeyEvent), como o controle físico: injetar
        // eventos exige INJECT_EVENTS, que é só do sistema (Android 14 recusa até na própria janela).
        RemoteBus.send(RemoteCommand.Key(code))
        return ok()
    }

    /** Foto do celular → wallpaper.jpg (reduzida para no máximo 1920 px) e "Minha imagem". */
    private fun wallpaper(body: ByteArray): JSONObject {
        if (body.isEmpty()) return err("Nenhuma foto recebida")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(body, 0, body.size, bounds)
        if (bounds.outWidth <= 0) return err("Arquivo não é uma imagem")
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(body, 0, body.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return err("Não consegui ler a imagem")
        val file = File(context.getExternalFilesDir(null), "wallpaper.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        runBlocking { prefs.wallpaperChanged() }
        return ok()
    }

    private fun state(): JSONObject {
        val s = runBlocking { prefs.settings.first() }
        val list = apps.apps().filter { it.packageName !in s.hidden }
        val dock = s.dock.filter { pkg -> list.any { it.packageName == pkg } }
        val ordered = dock.mapNotNull { pkg -> list.firstOrNull { it.packageName == pkg } } + list.filter { it.packageName !in dock }.sortedBy { it.label.lowercase() }
        return JSONObject()
            .put("ok", true)
            .put("foreground", RemoteBus.foreground)
            .put("apps", JSONArray(ordered.map { JSONObject().put("pkg", it.packageName).put("label", it.label).put("dock", it.packageName in dock) }))
            .put("style", StylePreset.current(s)?.name ?: JSONObject.NULL)
            .put("styles", JSONArray(StylePreset.entries.map { JSONObject().put("name", it.name).put("label", it.label).put("hint", it.hint) }))
            .put("background", s.background.name)
            .put("backgrounds", JSONArray(Background.entries.map { JSONObject().put("name", it.name).put("label", it.label).put("credit", it.credit ?: JSONObject.NULL) }))
            .put("rotation", s.bgRotation.name)
            .put("rotations", JSONArray(BgRotation.entries.map { JSONObject().put("name", it.name).put("label", it.label) }))
            .put("font", s.look.font.name)
            .put("fonts", JSONArray(FontChoice.entries.map { JSONObject().put("name", it.name).put("label", it.label) }))
            .put("accent", s.look.accent.name)
            .put("accents", JSONArray(Accent.entries.map { JSONObject().put("name", it.name).put("label", it.label).put("color", String.format("#%06X", it.argb and 0xFFFFFF)) }))
    }

    private fun icon(pkg: String): ByteArray? = icons.getOrPut(pkg) {
        val d = runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return null
        val bmp = d.toBitmap(128, 128, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    // ---------------------------------------------------------------- utilidades HTTP

    private fun read(input: InputStream): Request? {
        val head = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val c = input.read()
            if (c < 0) return null
            head.write(c)
            last4 = (last4 shl 8) or c
            if (last4 == 0x0D0A0D0A) break
            if (head.size() > 16_384) return null
        }
        val lines = head.toString("UTF-8").split("\r\n")
        val parts = lines.first().split(" ")
        if (parts.size < 2) return null
        val headers = lines.drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
        val len = (headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_BODY)
        val body = ByteArray(len)
        var off = 0
        while (off < len) { val n = input.read(body, off, len - off); if (n < 0) break; off += n }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to java.net.URLDecoder.decode(it[1], "UTF-8") } }.toMap()
        return Request(parts[0], path, query, headers, body)
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray, cache: Boolean = false, attachment: String? = null) {
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error" }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: ${if (cache) "max-age=86400" else "no-store"}\r\n" +
            (if (attachment != null) "Content-Disposition: attachment; filename=\"$attachment\"\r\n" else "") +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray()); out.write(body); out.flush()
    }

    private fun json(out: OutputStream, code: Int, o: JSONObject) = respond(out, code, "application/json; charset=utf-8", o.toString().toByteArray())

    private fun isLocal(a: InetAddress): Boolean = a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress

    companion object {
        val PORTS = listOf(8787, 8788, 8789)
        private const val MAX_BODY = 12 * 1024 * 1024

        /** IPv4 da TV na rede local (Wi-Fi ou cabo), para o QR. */
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()

        @Volatile var instance: RemoteServer? = null
    }
}
