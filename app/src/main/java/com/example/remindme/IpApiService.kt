package com.example.remindme

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class IpLocationResponse(
    val status: String,
    val country: String,
    val city: String,
    val lat: Double,
    val lon: Double
)

interface IpApiService {
    @GET("json/?fields=status,country,city,lat,lon")
    suspend fun getLocation(): IpLocationResponse

    companion object {
        private const val BASE_URL = "http://ip-api.com/"

        fun create(): IpApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(IpApiService::class.java)
        }
    }
}