package com.calebms.openflix.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val avatarColorHex: Long
)


@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey
    val id: String,
    val title: String,
    val type: String,
    val releaseYear: Int?,
    val localUri: String,
    val posterPath: String? = null,
    val overview: String? = null,
    val durationMs: Long? = null,
    val tmdbId: Int? = null,
    val backdropPath: String? = null,
    val voteAverage: Double? = null,
    val isTmdbSyncAttempted: Boolean = false,
    val isAvailable: Boolean = true,
    val subtitleUri: String? = null,
    val genres: String? = null

)


@Entity(
    tableName = "media_episodes",
    foreignKeys = [
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = ["id"],
            childColumns = ["mediaItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaItemId"])]
)
data class MediaEpisode(
    @PrimaryKey
    val id: String,
    val mediaItemId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String?,
    val localFileUri: String,
    val durationMs: Long? = null,
    val tmdbEpisodeId: Int? = null,
    val episodeOverview: String? = null,
    val stillPath: String? = null,
    val isAvailable: Boolean = true,
    val subtitleUri: String? = null
)


@Entity(
    tableName = "playback_status",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = ["id"],
            childColumns = ["mediaItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["mediaItemId"])
    ]
)
data class PlaybackStatus(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val profileId: Int,
    val mediaItemId: String,
    val episodeId: String? = null,
    val lastPositionMs: Long,
    val totalDurationMs: Long,
    val lastWatchedAt: Long = System.currentTimeMillis(),
    val isFinished: Boolean = false
)