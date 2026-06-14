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
    suspend fun fetchCurrentWeather(latitude: Double, longitude: Double): WeatherFetchResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val forecastUrl = buildForecastUrl(latitude, longitude)
                Log.i(TAG, "Open-Meteo URL: $forecastUrl")
                val url = URL(forecastUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                try {
                    val httpCode = connection.responseCode
                    Log.i(TAG, "Open-Meteo HTTP code: $httpCode")

                    val body = if (httpCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    }

                    if (body.isNotBlank()) {
                        Log.d(TAG, "Open-Meteo body start: ${body.take(BODY_LOG_LIMIT)}")
                    }

                    if (httpCode in 200..299) {
                        parseWeather(body)?.let { WeatherFetchResult.Success(it) }
                            ?: WeatherFetchResult.Failure("Missing or invalid weather fields").also {
                                Log.w(TAG, "Open-Meteo error: ${it.message}")
                            }
                    } else {
                        WeatherFetchResult.Failure("HTTP $httpCode: ${body.take(BODY_LOG_LIMIT)}").also {
                            Log.w(TAG, "Open-Meteo error: ${it.message}")
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { error ->
                Log.e(
                    TAG,
                    "Open-Meteo error: ${error::class.java.simpleName}: ${error.message}",
                    error
                )
            }.getOrElse { error ->
                WeatherFetchResult.Failure("${error::class.java.simpleName}: ${error.message}")
            }
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
        private const val BODY_LOG_LIMIT = 500
        private val json = Json { ignoreUnknownKeys = true }
    }
}

sealed class WeatherFetchResult {
    data class Success(val snapshot: WeatherSnapshot) : WeatherFetchResult()
    data class Failure(val message: String) : WeatherFetchResult()
}

data class WeatherSnapshot(
    val temperatureC: Float?,
    val windKmh: Float?,
    val rainProbability: Int?
)
