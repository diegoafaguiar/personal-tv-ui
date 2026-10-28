package tech.futureme.tvhome.data

import android.util.Log
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Seção do time. Duas fontes sem chave:
 *  - YouTube RSS dos canais (título, miniatura, link que o app do YouTube abre);
 *  - API pública da ESPN (site.api.espn.com): próximo jogo, último resultado, tabela.
 * Tudo falha em silêncio e o diagnóstico mostra o que respondeu.
 */
class FlamengoRepository {
    data class Match(val opponent: String, val home: Boolean, val dateMs: Long, val competition: String, val score: String?, val venue: String?)
    data class TeamInfo(val next: Match?, val last: Match?, val position: Int?, val points: Int?, val played: Int?, val form: String?)
    data class Result(val info: TeamInfo?, val videos: List<MediaItem>, val errors: List<String>)

    fun fetch(teamId: String, league: String, youtubeChannels: List<String>): Result {
        val errors = ArrayList<String>()
        val videos = youtubeChannels.flatMap { id ->
            try { youtubeFeed(id) } catch (e: Exception) { errors += "youtube $id: $e"; Log.w("TvHome", "RSS falhou", e); emptyList() }
        }.sortedByDescending { it.lastEngagementMs }
        val info = try { espn(teamId, league) } catch (e: Exception) { errors += "espn: $e"; Log.w("TvHome", "ESPN falhou", e); null }
        return Result(info, videos, errors)
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) tvhome/0.5")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    /** RSS oficial do YouTube: https://www.youtube.com/feeds/videos.xml?channel_id=... (sem chave). */
    private fun youtubeFeed(channelId: String): List<MediaItem> {
        val xml = get("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
        val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); setInput(xml.reader()) }
        val out = ArrayList<MediaItem>()
        var videoId = ""; var title = ""; var thumb: String? = null; var desc: String? = null; var published = 0L; var channel = ""
        var inEntry = false
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> { inEntry = true; videoId = ""; title = ""; thumb = null; desc = null; published = 0L }
                    "videoId" -> if (inEntry) videoId = parser.nextText()
                    "title" -> if (inEntry) title = parser.nextText() else channel = parser.nextText()
                    "thumbnail" -> if (inEntry) thumb = parser.getAttributeValue(null, "url")
                    "description" -> if (inEntry) desc = parser.nextText()
                    "published" -> if (inEntry) published = try { iso.parse(parser.nextText())?.time ?: 0L } catch (e: Exception) { 0L }
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry" && videoId.isNotBlank()) {
                    inEntry = false
                    out += MediaItem(
                        id = videoId.hashCode().toLong(), packageName = YOUTUBE, title = title, description = desc?.take(200),
                        posterUri = thumb, thumbnailUri = thumb, type = 4, genres = listOf("SPORTS"),
                        intentUri = "https://www.youtube.com/watch?v=$videoId", lastEngagementMs = published,
                        isWatchNext = false, channelName = channel, episodeTitle = null,
                    )
                }
            }
            ev = parser.next()
        }
        return out
    }

    /** Agenda e tabela do time na ESPN. */
    private fun espn(teamId: String, league: String): TeamInfo {
        val sched = JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/$league/teams/$teamId/schedule"))
        val events: JSONArray = sched.optJSONArray("events") ?: JSONArray()
        val now = System.currentTimeMillis()
        val matches = ArrayList<Match>()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            val comp = e.getJSONArray("competitions").getJSONObject(0)
            val competitors = comp.getJSONArray("competitors")
            var us: JSONObject? = null; var them: JSONObject? = null
            for (j in 0 until competitors.length()) {
                val c = competitors.getJSONObject(j)
                if (c.getJSONObject("team").optString("id") == teamId) us = c else them = c
            }
            if (us == null || them == null) continue
            val date = try { iso.parse(e.optString("date"))?.time ?: 0L } catch (ex: Exception) { 0L }
            val finished = comp.optJSONObject("status")?.optJSONObject("type")?.optBoolean("completed") == true
            val score = if (finished) "${us.optJSONObject("score")?.optString("displayValue") ?: us.optString("score")} x ${them.optJSONObject("score")?.optString("displayValue") ?: them.optString("score")}" else null
            matches += Match(
                opponent = them.getJSONObject("team").optString("shortDisplayName", them.getJSONObject("team").optString("displayName")),
                home = us.optString("homeAway") == "home",
                dateMs = date,
                competition = e.optJSONObject("season")?.optString("displayName") ?: e.optString("name"),
                score = score,
                venue = comp.optJSONObject("venue")?.optString("fullName"),
            )
        }
        val next = matches.filter { it.score == null && it.dateMs > now - 2 * 3600_000L }.minByOrNull { it.dateMs }
        val last = matches.filter { it.score != null }.maxByOrNull { it.dateMs }

        var position: Int? = null; var points: Int? = null; var played: Int? = null; var form: String? = null
        try {
            val st = JSONObject(get("https://site.api.espn.com/apis/v2/sports/soccer/$league/standings"))
            val entries: JSONArray = st.optJSONArray("children")?.optJSONObject(0)?.optJSONObject("standings")?.optJSONArray("entries") ?: JSONArray()
            for (i in 0 until entries.length()) {
                val en = entries.getJSONObject(i)
                if (en.optJSONObject("team")?.optString("id") != teamId) continue
                val stats: JSONArray = en.optJSONArray("stats") ?: JSONArray()
                for (k in 0 until stats.length()) {
                    val s = stats.getJSONObject(k)
                    when (s.optString("name")) {
                        "rank" -> position = s.optInt("value")
                        "points" -> points = s.optInt("value")
                        "gamesPlayed" -> played = s.optInt("value")
                    }
                }
                form = en.optString("note").ifBlank { null }
            }
        } catch (e: Exception) { Log.w("TvHome", "Tabela indisponível: $e") }
        return TeamInfo(next, last, position, points, played, form)
    }

    companion object { const val YOUTUBE = "com.google.android.youtube.tv" }
}
