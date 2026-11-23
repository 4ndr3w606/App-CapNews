package com.example.newsapp.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class AuthCredentials(
    val email: String,
    val password: String,
    val name: String? = null
)

data class AuthUser(
    val id: Long,
    val email: String
)

data class AuthSessionResponse(
    val user: AuthUser,
    val token: String,
    val expiresAt: Long
)

data class AuthUserResponse(
    val user: AuthUser,
    val expiresAt: Long
)

interface AuthApiService {
    @POST("api/auth/register")
    suspend fun register(@Body credentials: AuthCredentials): Response<AuthSessionResponse>

    @POST("api/auth/login")
    suspend fun login(@Body credentials: AuthCredentials): Response<AuthSessionResponse>

    @GET("api/auth/me")
    suspend fun currentUser(@Header("Authorization") authorization: String): Response<AuthUserResponse>

    @POST("api/auth/logout")
    suspend fun logout(@Header("Authorization") authorization: String): Response<Unit>
}
