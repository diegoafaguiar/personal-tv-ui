package io.github.diegoafaguiar.sintonia.data

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/** Uma manchete (RSS do Google Notícias, sem chave). */
data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val publishedMs: Long,
    val summary: String?,
    /** Assunto que gerou a busca (ex.: "Flamengo", "Netflix"), para o rótulo do card. */
    val topic: String,
)

data class NewsContent(
    val team: List<NewsItem> = emptyList(),
    val tech: List<NewsItem> = emptyList(),
    /** Estreias e lançamentos nos streamings que estão instalados. */
    val releases: List<NewsItem> = emptyList(),
    val fetchedAt: Long = 0L,
)

/**
 * Notícias pelo RSS do Google Notícias: `news.google.com/rss/search?q=<busca>&hl=pt-BR&gl=BR&ceid=BR:pt-419`.
 * Sem chave, em português, com fonte e data. Os links são redirecionadores do Google; na TV,
 * sem navegador, a tela de leitura mostra título, fonte e resumo.
 */
class NewsRepository {
    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) tvhome/0.7")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    fun search(query: String, topic: String = query, max: Int = 12): List<NewsItem> = try {
        val url = "https://news.google.com/rss/search?q=${URLEncoder.encode(query, "UTF-8")}&hl=pt-BR&gl=BR&ceid=BR:pt-419"
        parse(get(url), topic).take(max)
    } catch (e: Exception) {
        Log.w("TvHome", "Notícias falharam para '$query': $e"); emptyList()
    }

    private fun parse(xml: String, topic: String): List<NewsItem> {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val out = ArrayList<NewsItem>()
        val rfc = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        var inItem = false
        var title = ""; var link = ""; var source = ""; var date = 0L; var desc: String? = null
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> { inItem = true; title = ""; link = ""; source = ""; date = 0L; desc = null }
                    "title" -> if (inItem) title = parser.nextText()
                    "link" -> if (inItem) link = parser.nextText()
                    "source" -> if (inItem) source = parser.nextText()
                    "pubDate" -> if (inItem) date = try { rfc.parse(parser.nextText())?.time ?: 0L } catch (e: Exception) { 0L }
                    "description" -> if (inItem) desc = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                    inItem = false
                    if (title.isNotBlank()) {
                        // O título vem "Manchete - Fonte"; tira a fonte do fim quando repetida
                        val clean = if (source.isNotBlank() && title.endsWith(" - $source")) title.removeSuffix(" - $source") else title
                        // A descrição do Google Notícias é a manchete repetida + manchetes de outras fontes:
                        // só vira resumo se trouxer texto próprio.
                        val summary = desc?.let(::stripHtml)?.takeIf { d -> !d.startsWith(clean.take(40)) && !d.startsWith(title.take(40)) }?.take(400)
                        out += NewsItem(clean, link, source, date, summary, topic)
                    }
                }
            }
            ev = parser.next()
        }
        return out
    }

    private fun stripHtml(s: String): String = s.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace(Regex("\\s+"), " ").trim()
}
