package org.eu.manekineko.courier.data.api

import org.eu.manekineko.courier.data.model.DeliveryPoint
import org.eu.manekineko.courier.data.model.PointStatus
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.random.Random

class MockDeliveryApiService : DeliveryApiService {

    private val basePoints = listOf(
        PointData("1", 0.005, 0.003, "ул. Ленина, 15", "Иванов А.С.", PointStatus.PENDING, "10 мин"),
        PointData("2", -0.003, 0.007, "пр. Мира, 42", "Петрова М.В.", PointStatus.PENDING, "15 мин"),
        PointData("3", 0.008, -0.004, "ул. Гагарина, 8", "Сидоров К.Л.", PointStatus.IN_PROGRESS, "5 мин"),
        PointData("4", -0.006, -0.005, "ул. Пушкина, 23", "Козлова Е.Н.", PointStatus.PENDING, "20 мин"),
        PointData("5", 0.002, 0.009, "Бульвар Победы, 7", "Морозов Д.А.", PointStatus.DELIVERED, "—"),
        PointData("6", -0.009, 0.001, "ул. Чехова, 31", "Новикова О.П.", PointStatus.PENDING, "25 мин"),
        PointData("7", 0.004, -0.008, "пр. Строителей, 55", "Волков Р.И.", PointStatus.IN_PROGRESS, "8 мин"),
        PointData("8", -0.001, 0.006, "ул. Лермонтова, 12", "Соколова А.Г.", PointStatus.PENDING, "30 мин"),
    )

    override suspend fun getDeliveryPoints(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<DeliveryPoint> {
        delay(300 + Random.nextLong(200))

        val lngScale = cos(Math.toRadians(latitude))
        return basePoints.map { point ->
            DeliveryPoint(
                id = point.id,
                latitude = latitude + point.latOffset,
                longitude = longitude + point.lngOffset / lngScale,
                address = point.address,
                clientName = point.clientName,
                status = point.status,
                estimatedTime = point.estimatedTime
            )
        }
    }

    private data class PointData(
        val id: String,
        val latOffset: Double,
        val lngOffset: Double,
        val address: String,
        val clientName: String,
        val status: PointStatus,
        val estimatedTime: String
    )
}
