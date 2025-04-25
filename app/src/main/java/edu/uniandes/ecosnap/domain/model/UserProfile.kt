package edu.uniandes.ecosnap.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "",
    val email: String = "",
    val userName: String = "",
    val password: String = "",
    val points: Int = 0,
    val isAnonymous: Boolean = false
)