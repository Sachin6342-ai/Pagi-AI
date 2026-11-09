package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.Request

data class LiveWeatherReport(
    val locationName: String,
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val conditionDescription: String,
    val highCelsius: Double,
    val lowCelsius: Double
)

class WeatherService {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeatherForCity(cityQuery: String): Result<LiveWeatherReport> = withContext(Dispatchers.IO) {
        try {
            val cleanCity = cityQuery.trim().ifBlank { "New Delhi" }
            val encodedCity = URLEncoder.encode(cleanCity, "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json"
            val geoReq = Request.Builder().url(geoUrl).build()

            var lat = 28.6139
            var lon = 77.2090
            var resolvedName = cleanCity

            httpClient.newCall(geoReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val first = results.getJSONObject(0)
                        lat = first.optDouble("latitude", lat)
                        lon = first.optDouble("longitude", lon)
                        val name = first.optString("name", cleanCity)
                        val country = first.optString("country", "")
                        resolvedName = if (country.isNotBlank()) "$name, $country" else name
                    }
                }
            }

            fetchWeatherByCoordinates(lat, lon, resolvedName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWeatherByCoordinates(
        latitude: Double,
        longitude: Double,
        locationLabel: String = "Current Location"
    ): Result<LiveWeatherReport> = withContext(Dispatchers.IO) {
        try {
            val weatherUrl =
                "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
            val req = Request.Builder().url(weatherUrl).build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Weather HTTP ${resp.code}"))
                }
                val body = resp.body?.string() ?: return@withContext Result.failure(IllegalStateException("Empty weather body"))
                val root = JSONObject(body)
                val current = root.getJSONObject("current")
                val temp = current.optDouble("temperature_2m", 26.0)
                val feels = current.optDouble("apparent_temperature", temp)
                val humidity = current.optInt("relative_humidity_2m", 50)
                val wind = current.optDouble("wind_speed_10m", 8.0)
                val code = current.optInt("weather_code", 0)

                var high = temp + 3.0
                var low = temp - 3.0
                val daily = root.optJSONObject("daily")
                if (daily != null) {
                    val maxArr = daily.optJSONArray("temperature_2m_max")
                    val minArr = daily.optJSONArray("temperature_2m_min")
                    if (maxArr != null && maxArr.length() > 0) high = maxArr.optDouble(0, high)
                    if (minArr != null && minArr.length() > 0) low = minArr.optDouble(0, low)
                }

                Result.success(
                    LiveWeatherReport(
                        locationName = locationLabel,
                        temperatureCelsius = temp,
                        feelsLikeCelsius = feels,
                        humidityPercent = humidity,
                        windSpeedKmh = wind,
                        conditionDescription = mapWmoCodeToDescription(code),
                        highCelsius = high,
                        lowCelsius = low
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapWmoCodeToDescription(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Light Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snowfall"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Fair Conditions"
    }
}
