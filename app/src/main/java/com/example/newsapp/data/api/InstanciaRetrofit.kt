package com.example.newsapp.data.api

import android.net.Network
import com.example.newsapp.BuildConfig
import java.net.InetAddress
import okhttp3.Dns
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object InstanciaRetrofit {
    fun create(network: Network? = null): ApiService {
        val client = OkHttpClient.Builder()
            .apply {
                if (network != null) {
                    socketFactory(network.socketFactory)
                    dns(object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> =
                            network.getAllByName(hostname).toList()
                    })
                }
            }
            .followRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-Api-Key", BuildConfig.GNEWS_API_KEY)
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl("https://gnews.io/api/v4/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
