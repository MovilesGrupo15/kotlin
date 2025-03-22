package edu.uniandes.ecosnap.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DetectionResult(
    val bbox: List<Float>,
    val type: String,
    val confidence: Float
)