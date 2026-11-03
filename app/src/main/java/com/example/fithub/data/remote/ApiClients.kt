package com.example.fithub.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** wger Workout Manager REST API — free, open-source exercise database. */
interface WgerApi {
    @GET("api/v2/exerciseinfo/")
    suspend fun getExercises(
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0
    ): WgerExercisePage
}

object ApiClients {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val wger: WgerApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://wger.de/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WgerApi::class.java)
    }

    val weather: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }
}
