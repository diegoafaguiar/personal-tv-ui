package tech.futureme.tvhome.data

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cotações do dia (dólar e euro em reais) pela AwesomeAPI: gratuita, sem chave,
 * brasileira. https://docs.awesomeapi.com.br/api-de-moedas
 * Única chamada de rede do launcher além dos pôsteres. Falhou? Some da tela.
 */
class RatesRepository {
    data class Rate(val code: String, val bid: Double, val pctChange: Double)

    fun fetch(): List<Rate> = try {
        val conn = (URL("https://economia.awesomeapi.com.br/last/USD-BRL,EUR-BRL").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 5000
            setRequestProperty("User-Agent", "tvhome/0.2")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(body)
        listOf("USDBRL", "EURBRL").mapNotNull { key ->
            json.optJSONObject(key)?.let { o ->
                Rate(o.optString("code", key.take(3)), o.optString("bid").toDoubleOrNull() ?: return@let null, o.optString("pctChange").toDoubleOrNull() ?: 0.0)
            }
        }
    } catch (e: Exception) {
        Log.w("TvHome", "Cotações indisponíveis: $e")
        emptyList()
    }
}
