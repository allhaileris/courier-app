package org.eu.manekineko.courier.data.model

data class DeliveryPoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val clientName: String,
    val status: PointStatus,
    val estimatedTime: String
)

enum class PointStatus {
    PENDING,
    IN_PROGRESS,
    DELIVERED
}
