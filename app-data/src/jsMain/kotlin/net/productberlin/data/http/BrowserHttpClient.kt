package net.productberlin.data.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createBrowserHttpClient(): HttpClient =
    HttpClient(Js) {
        expectSuccess = true
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
