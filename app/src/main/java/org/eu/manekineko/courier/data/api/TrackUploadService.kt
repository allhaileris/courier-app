package org.eu.manekineko.courier.data.api

import android.util.Log
import org.eu.manekineko.courier.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TrackUploadService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun notifyTrackStarted(
        latitude: Double,
        longitude: Double,
        timestamp: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("event", "track_started")
                put("latitude", latitude)
                put("longitude", longitude)
                put("timestamp", timestamp)
            }

            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}${ApiConfig.TRACK_START_ENDPOINT}")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(response.body?.string() ?: "")
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify track start", e)
            Result.failure(e)
        }
    }

    suspend fun uploadTrack(
        trackId: Long,
        pointsJson: String,
        startTime: Long,
        comment: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val pointsArray = JSONArray(pointsJson)
            val json = JSONObject().apply {
                put("trackId", trackId)
                put("startTime", startTime)
                put("comment", comment)
                put("pointsCount", pointsArray.length())
                put("points", pointsArray)
            }

            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}${ApiConfig.TRACK_UPLOAD_ENDPOINT}")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(response.body?.string() ?: "")
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload track", e)
            Result.failure(e)
        }
    }

    private const val TAG = "TrackUploadService"
}
