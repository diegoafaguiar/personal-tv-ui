package io.github.diegoafaguiar.sintonia.data

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Busca de vídeos no YouTube sem chave: lê o "ytInitialData" da página de resultados e extrai
 * id, título, canal e duração com expressões regulares (sem montar o JSON de ~1 MB, que pesa numa
 * TV de 1 GB). O RSS de canais (/feeds/videos.xml) responde 404 desde out/2026.
 * Os cards abrem no app do YouTube por intent explícito.
 */
class YoutubeRepository {
    data class Video(val id: String, val title: String, val channel: String, val duration: String?)

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000; readTimeout = 9000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36")
            setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    /** [recent] = ordenar por data de envio; senão relevância. Só vídeos (sem canais/playlists). */
    fun search(query: String, max: Int = 10, recent: Boolean = false): List<Video> = try {
        val sp = if (recent) "CAISAhAB" else "EgIQAQ%253D%253D"
        val html = get("https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}&sp=$sp")
        val out = ArrayList<Video>()
        var from = 0
        while (out.size < max) {
            val i = html.indexOf("\"videoRenderer\":{\"videoId\":\"", from)
            if (i < 0) break
            val chunk = html.substring(i, minOf(html.length, i + 6000))
            from = i + 30
            val id = ID.find(chunk)?.groupValues?.get(1) ?: continue
            val title = TITLE.find(chunk)?.groupValues?.get(1)?.let(::unescape) ?: continue
            val channel = OWNER.find(chunk)?.groupValues?.get(1)?.let(::unescape) ?: ""
            val dur = LENGTH.find(chunk)?.groupValues?.get(1)
            if (out.none { it.id == id }) out += Video(id, title, channel, dur)
        }
        out
    } catch (e: Exception) {
        Log.w("TvHome", "YouTube falhou para '$query': $e"); emptyList()
    }

    /** Vídeo como MediaItem: miniatura do ytimg e intent explícito para o app do YouTube. */
    fun toItem(v: Video, genre: String = "MUSIC"): MediaItem = MediaItem(
        id = v.id.hashCode().toLong(), packageName = YOUTUBE, title = v.title, description = null,
        posterUri = "https://i.ytimg.com/vi/${v.id}/hqdefault.jpg", thumbnailUri = "https://i.ytimg.com/vi/${v.id}/hqdefault.jpg",
        type = 4, genres = listOf(genre),
        intentUri = "intent://www.youtube.com/watch?v=${v.id}#Intent;scheme=https;package=$YOUTUBE;end",
        lastEngagementMs = 0L, isWatchNext = false, channelName = v.channel,
        episodeTitle = listOfNotNull(v.channel.ifBlank { null }, v.duration).joinToString(" · ").ifBlank { null },
    )

    private fun unescape(s: String) = s.replace("\\u0026", "&").replace("\\\"", "\"").replace("\\u003c", "<").replace("\\u003e", ">").replace("\\/", "/")

    companion object {
        const val YOUTUBE = "com.google.android.youtube.tv"
        private val ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
        private val TITLE = Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")
        private val OWNER = Regex("\"ownerText\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")
        private val LENGTH = Regex("\"lengthText\":\\{[^}]*?\"simpleText\":\"([0-9:]+)\"")
    }
}
