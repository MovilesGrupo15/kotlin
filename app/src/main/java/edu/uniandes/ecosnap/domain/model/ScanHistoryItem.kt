package edu.uniandes.ecosnap.domain.model

@kotlinx.serialization.Serializable
data class ScanHistoryItem(
    val id: String,
    val detectedType: String,
    val confidence: Float,
    val timestamp: Long,
    val locationName: String? = null
)