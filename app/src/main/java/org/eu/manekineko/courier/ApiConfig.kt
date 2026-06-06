package org.eu.manekineko.courier

/**
 * Настройки подключения к бекенду.
 * Измените BASE_URL на адрес вашего сервера.
 */
object ApiConfig {
    /** Базовый URL бекенда (без trailing slash) */
    const val BASE_URL = "https://api.example.com"

    /** Эндпоинт начала записи трека */
    const val TRACK_START_ENDPOINT = "/api/tracks/start"

    /** Эндпоинт выгрузки записанного трека */
    const val TRACK_UPLOAD_ENDPOINT = "/api/tracks/upload"

    /** Таймаут соединения (секунды) */
    const val CONNECT_TIMEOUT_SECONDS = 15L

    /** Таймаут чтения/записи (секунды) */
    const val READ_WRITE_TIMEOUT_SECONDS = 30L
}
