package com.example.newsapp.data.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("top-headlines")
    suspend fun getTopHeadlines(
        @Query("category") category: String,
        @Query("lang") lang: String = "es",
        @Query("apikey") apiKey: String
    ): Response<NewsResponse>


    @GET("search")
    suspend fun buscarNoticias(
        @Query("q") consulta: String,
        @Query("lang") lang: String = "es",
        @Query("apikey") apiKey: String
    ): Response<NewsResponse>
}