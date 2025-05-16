package edu.uniandes.ecosnap.domain.model

data class RecyclingGuideItem(
    val id: String,
    val title: String,
    val wasteType: String,
    val category: String,
    val description: String,
    val instructions: String,
    val resourceUrl: String = ""
)