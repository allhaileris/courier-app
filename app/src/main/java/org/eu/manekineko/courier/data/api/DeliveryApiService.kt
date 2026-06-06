package org.eu.manekineko.courier.data.api

import org.eu.manekineko.courier.data.model.DeliveryPoint

interface DeliveryApiService {
    suspend fun getDeliveryPoints(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 5.0
    ): List<DeliveryPoint>
}
