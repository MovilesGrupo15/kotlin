package edu.uniandes.ecosnap.data.repository

import android.util.Log
import edu.uniandes.ecosnap.domain.model.UserProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class UserRepository {
    private val baseUrl = "http://192.168.1.107:8000"
    private val client = HttpClient(Android) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    fun getUserProfile(): Flow<UserProfile> = flow {
        try {
            Log.d("UserRepository", "Getting user profile...")
            val userProfile = client.get<UserProfile>("$baseUrl/api/user")
            Log.d("UserRepository", "Success! User profile received: $userProfile")
            emit(userProfile)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error getting user profile: ${e.message}")
            throw e
        }
    }
}