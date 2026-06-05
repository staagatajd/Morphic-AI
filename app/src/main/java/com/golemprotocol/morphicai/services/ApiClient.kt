package com.golemprotocol.morphicai.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SignInRequest(val email: String, val password: String)

@Serializable
data class SignUpRequest(val username: String, val email: String, val password: String)

@Serializable
data class AuthResponse(
    val success: Int,
    val accessToken: String? = null,
    val username: String? = null,
    val role: String? = null,
    val message: String
)

object ApiClient {
    private const val BASE_URL = "https://golem-protocol-api.vercel.app"

    val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            level = LogLevel.BODY
        }
        defaultRequest {
            url(BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun signIn(request: SignInRequest): AuthResponse {
        return client.post("/signin") {
            setBody(request)
        }.body()
    }

    suspend fun signUp(request: SignUpRequest): AuthResponse {
        return client.post("/signup") {
            setBody(request)
        }.body()
    }
}
