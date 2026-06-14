package fr.augustine.androgustine.data.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WeatherService {
    suspend fun fetchCurrentWeather(latitude: Double, longitude: Double): WeatherSnapshot? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(buildForecastUrl(latitude, longitude))
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                try {
                    if (connection.responseCode in 200..299) {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        parseWeather(body)
                    } else {
                        null
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { error ->
                Log.w(TAG, "Weather fetch failed: ${error.message}")
            }.getOrNull()
        }

    private fun buildForecastUrl(latitude: Double, longitude: Double): String =
        String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.8f&longitude=%.8f&current=temperature_2m,wind_speed_10m&hourly=precipitation_probability&timezone=auto&forecast_days=1",
            latitude,
            longitude
        )

    private fun parseWeather(jsonText: String): WeatherSnapshot? {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val current = root["current"]?.jsonObject ?: return null
        val currentTime = current["time"]?.jsonPrimitive?.content

        return WeatherSnapshot(
            temperatureC = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull?.toFloat(),
            windKmh = current["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull?.toFloat(),
            rainProbability = parseRainProbability(root["hourly"] as? JsonObject, currentTime)
        )
    }

    private fun parseRainProbability(hourly: JsonObject?, currentTime: String?): Int? {
        val probabilities = hourly?.get("precipitation_probability") as? JsonArray ?: return null
        val times = hourly["time"] as? JsonArray
        val currentHour = currentTime?.take(13)

        val matchingIndex = if (currentHour != null && times != null) {
            times.indexOfFirst { it.jsonPrimitive.content.take(13) == currentHour }
        } else {
            -1
        }

        val selectedIndex = matchingIndex.takeIf { it >= 0 } ?: 0
        return probabilities.getOrNull(selectedIndex)?.jsonPrimitive?.intOrNull
    }

    private companion object {
        private const val TAG = "WeatherService"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

data class WeatherSnapshot(
    val temperatureC: Float?,
    val windKmh: Float?,
    val rainProbability: Int?
)
