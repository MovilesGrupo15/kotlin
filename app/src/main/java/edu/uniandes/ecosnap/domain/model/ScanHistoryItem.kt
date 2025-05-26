package edu.uniandes.ecosnap.domain.model

@kotlinx.serialization.Serializable
data class ScanHistoryItem(
    val id: String,
    val imagePath: String = "",
    val detectedType: String,
    val confidence: Float,
    val timestamp: Long,
    val locationName: String? = null
)