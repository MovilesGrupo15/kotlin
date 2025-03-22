package edu.uniandes.ecosnap.data.observer

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import kotlinx.serialization.json.Json

object HttpClientProvider {
    fun createClient() = HttpClient(Android) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }
}