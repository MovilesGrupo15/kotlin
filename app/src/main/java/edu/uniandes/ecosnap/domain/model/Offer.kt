package edu.uniandes.ecosnap.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Offer(
    val id: String,
    val description: String,
    val points: Int,
    val imageUrl: String,
    val backgroundColor: Long? = null
)