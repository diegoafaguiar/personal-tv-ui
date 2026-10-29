package io.github.diegoafaguiar.sintonia.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cotações do dia (dólar e euro em reais) pela AwesomeAPI: gratuita, sem chave,
 * brasileira. https://docs.awesomeapi.com.br/api-de-moedas
 * Falhou? Some da tela.
 */
class RatesRepository {
    data class Rate(
        val code: String,
        val bid: Double,
        val pctChange: Double,
        val ask: Double = bid,
        val high: Double = bid,
        val low: Double = bid,
        val varBid: Double = 0.0,
        /** Horário da cotação (ms). */
        val atMs: Long = 0L,
    ) {
        val label: String get() = when (code) { "USD" -> "Dólar"; "EUR" -> "Euro"; else -> code }
    }

    /** Um fechamento diário (compra) para o gráfico. */
    data class Point(val atMs: Long, val bid: Double)

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 5000
            setRequestProperty("User-Agent", "tvhome/0.9")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun JSONObject.num(key: String): Double? = optString(key).toDoubleOrNull()

    fun fetch(): List<Rate> = try {
        val json = JSONObject(get("https://economia.awesomeapi.com.br/last/USD-BRL,EUR-BRL"))
        listOf("USDBRL", "EURBRL").mapNotNull { key ->
            json.optJSONObject(key)?.let { o ->
                val bid = o.num("bid") ?: return@let null
                Rate(
                    code = o.optString("code", key.take(3)), bid = bid, pctChange = o.num("pctChange") ?: 0.0,
                    ask = o.num("ask") ?: bid, high = o.num("high") ?: bid, low = o.num("low") ?: bid,
                    varBid = o.num("varBid") ?: 0.0, atMs = (o.optString("timestamp").toLongOrNull() ?: 0L) * 1000,
                )
            }
        }
    } catch (e: Exception) {
        Log.w("TvHome", "Cotações indisponíveis: $e")
        emptyList()
    }

    /** Últimos [days] dias de [code] (USD, EUR), do mais antigo para o mais novo. Vazio se falhar. */
    fun history(code: String, days: Int = 30): List<Point> = try {
        val arr = JSONArray(get("https://economia.awesomeapi.com.br/json/daily/$code-BRL/$days"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val bid = o.num("bid") ?: return@mapNotNull null
            Point((o.optString("timestamp").toLongOrNull() ?: return@mapNotNull null) * 1000, bid)
        }.sortedBy { it.atMs }
    } catch (e: Exception) {
        Log.w("TvHome", "Histórico de $code indisponível: $e")
        emptyList()
    }
}
