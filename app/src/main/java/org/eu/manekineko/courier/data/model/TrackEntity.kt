package org.eu.manekineko.courier.data.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "tracks")
@Parcelize
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "points_json")
    val pointsJson: String,

    @ColumnInfo(name = "start_time")
    val startTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "comment")
    val comment: String = ""
) : Parcelable
