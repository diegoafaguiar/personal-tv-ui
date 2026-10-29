package io.github.diegoafaguiar.sintonia.data

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Clima pela Open-Meteo: gratuita, sem chave. https://open-meteo.com/en/docs
 * Coordenadas padrão: Recreio dos Bandeirantes, Rio de Janeiro.
 */
class WeatherRepository {
    /** Previsão de um dia. [date] no formato da API (aaaa-mm-dd). */
    data class Day(val date: String, val code: Int, val maxC: Double, val minC: Double, val rainPct: Int) {
        val icon: String get() = iconFor(code)
        val label: String get() = describe(code)
    }

    data class Weather(
        val tempC: Double, val code: Int, val maxC: Double, val minC: Double, val rainPct: Int,
        val feelsC: Double? = null, val humidity: Int? = null,
        /** Próximos dias (sem hoje). */
        val next: List<Day> = emptyList(),
    ) {
        val label: String get() = describe(code)
        val icon: String get() = iconFor(code)
    }

    fun fetch(lat: Double, lon: Double): Weather? = try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,apparent_temperature,relative_humidity_2m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=America%2FSao_Paulo&forecast_days=4"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(body)
        val cur = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxs = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        val rain = daily.getJSONArray("precipitation_probability_max")
        val days = (0 until dates.length()).map { i -> Day(dates.getString(i), codes.optInt(i, 0), maxs.getDouble(i), mins.getDouble(i), rain.optInt(i, 0)) }
        Weather(
            tempC = cur.getDouble("temperature_2m"),
            code = cur.getInt("weather_code"),
            maxC = days[0].maxC,
            minC = days[0].minC,
            rainPct = days[0].rainPct,
            feelsC = cur.optDouble("apparent_temperature").takeIf { !it.isNaN() },
            humidity = cur.optInt("relative_humidity_2m", -1).takeIf { it >= 0 },
            next = days.drop(1).take(3),
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
        // \uFE0F pede a versão colorida (emoji): sem ele, ☁ e ☀ saem como texto cinza em algumas fontes
        fun iconFor(code: Int): String = when (code) {
            0, 1 -> "☀\uFE0F"; 2 -> "⛅\uFE0F"; 3 -> "☁\uFE0F"; 45, 48 -> "🌫\uFE0F"
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧\uFE0F"
            71, 73, 75, 77 -> "🌨\uFE0F"; 95, 96, 99 -> "⛈\uFE0F"; else -> ""
        }
    }
}
