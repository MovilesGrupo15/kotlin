package edu.uniandes.ecosnap.data.repository

import edu.uniandes.ecosnap.domain.model.Offer
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class OfferRepository {
    private val baseUrl = "http://192.168.1.107:8000"
    private val client = HttpClient(Android) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    fun getOffers(): Flow<List<Offer>> = flow {
        val offers = client.get<List<Offer>>("$baseUrl/api/offers")
        emit(offers)
    }
}