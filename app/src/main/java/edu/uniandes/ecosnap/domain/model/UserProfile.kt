package edu.uniandes.ecosnap.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val userName: String,
    val points: Int,
)