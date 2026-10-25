package tech.futureme.tvhome.data

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class MusicKind(val label: String) { TRACK("Música"), ALBUM("Álbum"), PLAYLIST("Playlist"), ARTIST("Artista") }

/** Sugestão musical: capa, título, artista/dono e o texto de busca que abre no app de música. */
data class MusicItem(
    val kind: MusicKind,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    /** Busca enviada ao Spotify (ou YouTube): "artista título". */
    val query: String,
)

data class MusicContent(
    val tracks: List<MusicItem> = emptyList(),
    val albums: List<MusicItem> = emptyList(),
    val playlists: List<MusicItem> = emptyList(),
    val artists: List<MusicItem> = emptyList(),
    /** Clipes no YouTube das mais tocadas (um por música). */
    val clips: List<MediaItem> = emptyList(),
    /** Vídeos musicais em alta no YouTube (ao vivo, coletâneas). */
    val youtube: List<MediaItem> = emptyList(),
    val fetchedAt: Long = 0L,
)

/**
 * Paradas da Deezer (`api.deezer.com/chart/0`), públicas e sem chave: mais tocadas, álbuns,
 * playlists editoriais e artistas, com capas. O card abre a busca no Spotify (se instalado)
 * ou no YouTube; a Deezer é só a fonte das sugestões.
 */
class MusicRepository {
    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) tvhome/0.7")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    fun chart(): MusicContent = try {
        val j = JSONObject(get("https://api.deezer.com/chart/0?limit=20"))
        fun items(key: String, map: (JSONObject) -> MusicItem): List<MusicItem> {
            val arr = j.optJSONObject(key)?.optJSONArray("data") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i -> runCatching { map(arr.getJSONObject(i)) }.getOrNull() }
        }
        MusicContent(
            tracks = items("tracks") { t ->
                val artist = t.optJSONObject("artist")?.optString("name") ?: ""
                MusicItem(MusicKind.TRACK, t.optString("title_short", t.optString("title")), artist, t.optJSONObject("album")?.optString("cover_medium"), "$artist ${t.optString("title_short", t.optString("title"))}")
            },
            albums = items("albums") { a ->
                val artist = a.optJSONObject("artist")?.optString("name") ?: ""
                MusicItem(MusicKind.ALBUM, a.optString("title"), artist, a.optString("cover_medium"), "$artist ${a.optString("title")}")
            },
            playlists = items("playlists") { p ->
                MusicItem(MusicKind.PLAYLIST, p.optString("title"), "${p.optInt("nb_tracks")} músicas", p.optString("picture_medium"), p.optString("title"))
            },
            artists = items("artists") { a ->
                MusicItem(MusicKind.ARTIST, a.optString("name"), "Artista", a.optString("picture_medium"), a.optString("name"))
            },
            fetchedAt = System.currentTimeMillis(),
        )
    } catch (e: Exception) {
        Log.w("TvHome", "Paradas musicais falharam: $e"); MusicContent()
    }
}
