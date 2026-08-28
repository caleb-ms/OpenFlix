package com.calebms.openflix.data.local.dao

import androidx.room.*
import com.calebms.openflix.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): Flow<List<Profile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile): Long

    @Delete
    suspend fun deleteProfile(profile: Profile)
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE isAvailable = 1")
    fun getAllMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE type = :type AND isAvailable = 1")
    fun getMediaByType(type: String): Flow<List<MediaItem>>


    @Upsert
    suspend fun insertMediaItems(items: List<MediaItem>)

    @Upsert
    suspend fun insertEpisodes(episodes: List<MediaEpisode>)

    @Query("SELECT * FROM media_episodes WHERE mediaItemId = :mediaId AND isAvailable = 1 ORDER BY seasonNumber, episodeNumber ASC")
    fun getEpisodesForShow(mediaId: String): Flow<List<MediaEpisode>>

    @Query("SELECT * FROM media_items WHERE isTmdbSyncAttempted = 0")
    suspend fun getUnsyncedMedia(): List<MediaItem>

    @Query("SELECT * FROM media_items")
    suspend fun getAllMediaList(): List<MediaItem>

    // Fetch episodes synchronously for background processing in the sync worker
    @Query("SELECT * FROM media_episodes WHERE mediaItemId = :mediaId")
    suspend fun getEpisodesListForShow(mediaId: String): List<MediaEpisode>

    @Query("UPDATE media_items SET isAvailable = :isAvailable WHERE id = :id")
    suspend fun updateMediaAvailability(id: String, isAvailable: Boolean)

    @Query("UPDATE media_episodes SET isAvailable = :isAvailable WHERE id = :id")
    suspend fun updateEpisodeAvailability(id: String, isAvailable: Boolean)
}

@Dao
interface PlaybackDao {
    @Query("""
        SELECT * FROM playback_status 
        WHERE profileId = :profileId AND isFinished = 0 
        ORDER BY lastWatchedAt DESC
    """)
    fun getContinueWatching(profileId: Int): Flow<List<PlaybackStatus>>

    @Query("""
        SELECT * FROM playback_status 
        WHERE profileId = :profileId AND mediaItemId = :mediaItemId 
        AND (episodeId = :episodeId OR (episodeId IS NULL AND :episodeId IS NULL))
        LIMIT 1
    """)
    suspend fun getExactPlaybackStatus(profileId: Int, mediaItemId: String, episodeId: String?): PlaybackStatus?

    @Query("SELECT * FROM playback_status WHERE profileId = :profileId AND mediaItemId = :mediaItemId")
    fun getStatusesForMedia(profileId: Int, mediaItemId: String): Flow<List<PlaybackStatus>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackStatus(status: PlaybackStatus)

    @Query("""
        UPDATE playback_status 
        SET lastPositionMs = :position, totalDurationMs = :duration, lastWatchedAt = :timestamp, isFinished = :isFinished 
        WHERE id = :statusId
    """)
    suspend fun updatePlaybackStatus(statusId: Int, position: Long, duration: Long, timestamp: Long, isFinished: Boolean)

    @Transaction
    suspend fun upsertPlayback(profileId: Int, mediaId: String, episodeId: String?, position: Long, duration: Long, isFinished: Boolean) {
        val existing = getExactPlaybackStatus(profileId, mediaId, episodeId)
        if (existing != null) {
            updatePlaybackStatus(existing.id, position, duration, System.currentTimeMillis(), isFinished)
        } else {
            insertPlaybackStatus(
                PlaybackStatus(
                    profileId = profileId,
                    mediaItemId = mediaId,
                    episodeId = episodeId,
                    lastPositionMs = position,
                    totalDurationMs = duration,
                    lastWatchedAt = System.currentTimeMillis(),
                    isFinished = isFinished
                )
            )
        }
    }
}