package edu.uniandes.ecosnap.domain.model

import kotlinx.serialization.Serializable
import org.osmdroid.util.GeoPoint

@Serializable
data class PointOfInterest(
    val id: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
}