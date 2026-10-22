package tech.futureme.tvhome.data

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Clima pela Open-Meteo: gratuita, sem chave. https://open-meteo.com/en/docs
 * Coordenadas padrão: Recreio dos Bandeirantes, Rio de Janeiro.
 */
class WeatherRepository {
    data class Weather(val tempC: Double, val code: Int, val maxC: Double, val minC: Double, val rainPct: Int) {
        val label: String get() = describe(code)
        val icon: String get() = iconFor(code)
    }

    fun fetch(lat: Double, lon: Double): Weather? = try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=America%2FSao_Paulo&forecast_days=1"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(body)
        val cur = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        Weather(
            tempC = cur.getDouble("temperature_2m"),
            code = cur.getInt("weather_code"),
            maxC = daily.getJSONArray("temperature_2m_max").getDouble(0),
            minC = daily.getJSONArray("temperature_2m_min").getDouble(0),
            rainPct = daily.getJSONArray("precipitation_probability_max").optInt(0, 0),
        )
    } catch (e: Exception) {
        Log.w("TvHome", "Clima indisponível: $e"); null
    }

    companion object {
        /** Códigos WMO usados pela Open-Meteo. */
        fun describe(code: Int): String = when (code) {
            0 -> "Céu limpo"; 1 -> "Quase limpo"; 2 -> "Parcialmente nublado"; 3 -> "Nublado"
            45, 48 -> "Neblina"
            51, 53, 55, 56, 57 -> "Garoa"
            61, 63, 65, 66, 67 -> "Chuva"
            71, 73, 75, 77 -> "Neve"
            80, 81, 82 -> "Pancadas de chuva"
            95, 96, 99 -> "Tempestade"
            else -> "—"
        }
        fun iconFor(code: Int): String = when (code) {
            0, 1 -> "☀"; 2 -> "⛅"; 3 -> "☁"; 45, 48 -> "🌫"
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧"
            71, 73, 75, 77 -> "🌨"; 95, 96, 99 -> "⛈"; else -> ""
        }
    }
}
