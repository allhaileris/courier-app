package org.eu.manekineko.courier.util

import org.eu.manekineko.courier.data.model.TrackEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TrackExporter {

    fun exportTracks(tracks: List<TrackEntity>): String {
        val exportDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
            .format(Date())

        val tracksArray = JSONArray()
        for (track in tracks) {
            try {
                val pointsJson = track.pointsJson
                val pointsArray = JSONArray(pointsJson)

                val trackObject = JSONObject().apply {
                    put("id", track.id)
                    put("start_time", track.startTime)
                    if (track.comment.isNotEmpty()) {
                        put("comment", track.comment)
                    }
                    put("points", pointsArray)
                }

                tracksArray.put(trackObject)
            } catch (e: Exception) {
                // Пропускаем треки с ошибками
            }
        }

        val exportObject = JSONObject().apply {
            put("export_date", exportDate)
            put("tracks_count", tracks.size)
            put("tracks", tracksArray)
        }

        return exportObject.toString(2) // Форматированный JSON с отступами
    }
}
