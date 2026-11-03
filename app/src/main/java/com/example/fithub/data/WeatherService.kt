package com.example.fithub.data

import com.example.fithub.data.remote.ApiClients
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

// Response models from the Open-Meteo API
data class WeatherResponse(
    @SerializedName("current_weather") val currentWeather: CurrentWeather?
)

data class CurrentWeather(
    @SerializedName("temperature") val temperature: Double?,
    @SerializedName("weathercode") val weathercode: Int?
)

interface WeatherApi {
    @GET("v1/forecast?current_weather=true")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): WeatherResponse
}

enum class WeatherCondition { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }

data class WeatherInfo(val temperatureC: Int, val condition: WeatherCondition)

object WeatherRepository {

    /** Returns null when the weather can't be loaded (offline, API down) — never throws. */
    suspend fun fetchCurrentWeather(latitude: Double, longitude: Double): WeatherInfo? {
        return try {
            val current = ApiClients.weather.getWeather(latitude, longitude).currentWeather
            val temp = current?.temperature ?: return null
            WeatherInfo(Math.round(temp).toInt(), parseWeatherCode(current.weathercode ?: 0))
        } catch (e: Exception) {
            null
        }
    }

    // Maps Open-Meteo WMO weather codes to conditions
    fun parseWeatherCode(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2, 3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        in 51..67, in 80..82 -> WeatherCondition.RAIN
        in 71..77, 85, 86 -> WeatherCondition.SNOW
        95, 96, 99 -> WeatherCondition.STORM
        else -> WeatherCondition.CLEAR
    }
}
