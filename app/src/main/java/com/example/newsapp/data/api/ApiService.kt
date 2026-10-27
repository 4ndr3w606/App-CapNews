package com.example.newsapp.data.api

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response

interface ApiService {

    @GET("top-headlines")
    suspend fun getTopHeadlines(
        @Query("category") category: String,
        @Query("lang") lang: String = "es",
        @Query("country") country: String? = null,
        @Query("apikey") apiKey: String
    ): Response<NewsResponse>

    @GET("search")
    suspend fun searchNews(
        @Query("q") query: String,
        @Query("lang") lang: String = "es",
        @Query("apikey") apiKey: String
    ): Response<NewsResponse>
}