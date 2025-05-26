package edu.uniandes.ecosnap.domain.model

@kotlinx.serialization.Serializable
data class VisitedPointItem(
    val id: String,
    val pointName: String,
    val address: String,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null
)
