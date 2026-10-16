package com.calebms.openflix.data.local

import com.calebms.openflix.data.local.entities.WatchHistoryEntry
import com.calebms.openflix.data.local.dao.WatchStats
import org.junit.Assert.*
import org.junit.Test

class WatchHistoryTest {

    @Test
    fun createWatchHistoryEntry_hasCorrectFields() {
        val entry = WatchHistoryEntry(
            id = 1L,
            profileId = 1,
            mediaItemId = "movie_123",
            episodeId = null,
            mediaTitle = "Inception",
            episodeTitle = null,
            mediaType = "MOVIE",
            seasonNumber = null,
            episodeNumber = null,
            durationMs = 8880000L,
            watchedDurationMs = 8800000L,
            watchedAt = 1700000000000L,
            cachedThumbPath = "/path/to/thumb.jpg",
            posterPath = "https://image.tmdb.org/t/p/w500/poster.jpg",
            isManualEntry = false
        )

        assertEquals("Inception", entry.mediaTitle)
        assertEquals("MOVIE", entry.mediaType)
        assertEquals(1, entry.profileId)
        assertFalse(entry.isManualEntry)
    }

    @Test
    fun watchStats_calculatesCorrectValues() {
        val stats = WatchStats(
            moviesCount = 5,
            episodesCount = 20,
            totalDurationMs = 360000000L // 100 hours
        )

        assertEquals(5, stats.moviesCount)
        assertEquals(20, stats.episodesCount)
        assertEquals(360000000L, stats.totalDurationMs)
    }
}
