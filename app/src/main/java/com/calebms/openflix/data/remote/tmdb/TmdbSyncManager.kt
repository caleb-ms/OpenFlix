package com.calebms.openflix.data.remote.tmdb

import android.content.Context
import android.util.Log
import com.calebms.openflix.data.local.AppDatabase
import com.calebms.openflix.data.local.entities.MediaEpisode
import com.calebms.openflix.data.local.entities.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class TmdbSyncManager(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val mediaDao = db.mediaDao()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val tmdbApi: TmdbApiService = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApiService::class.java)

    suspend fun syncLibrary(apiKey: String, force: Boolean = false) = withContext(Dispatchers.IO) {
        Log.d("TmdbSync", "Starting syncLibrary with API key length: ${apiKey.length}, force=$force")
        if (apiKey.isBlank()) {
            Log.d("TmdbSync", "API key is blank, skipping sync")
            return@withContext
        }

        val pendingItems = if (force) {
            mediaDao.getAllMediaList()
        } else {
            mediaDao.getUnsyncedMedia()
        }
        Log.d("TmdbSync", "Found ${pendingItems.size} items to sync")

        for (item in pendingItems) {
            try {
                Log.d("TmdbSync", "Syncing item: ${item.title} (Type: ${item.type})")
                if (item.type == "MOVIE") {
                    syncMovie(item, apiKey)
                } else if (item.type == "TV_SHOW") {
                    syncTvShow(item, apiKey)
                }
            } catch (e: Exception) {
                Log.e("TmdbSync", "Failed to sync ${item.title}", e)

                mediaDao.insertMediaItems(listOf(item.copy(isTmdbSyncAttempted = true)))
            }
        }
        Log.d("TmdbSync", "syncLibrary completed")
    }

    private suspend fun syncMovie(item: MediaItem, apiKey: String) {
        Log.d("TmdbSync", "Searching movie: ${item.title}")
        val response = tmdbApi.searchMovie(apiKey = apiKey, query = item.title, year = item.releaseYear)
        val bestMatch = response.results.firstOrNull()

        if (bestMatch != null) {
            Log.d("TmdbSync", "Found match for ${item.title}: ${bestMatch.title} (ID: ${bestMatch.id})")
            

            val extractedYear = bestMatch.release_date?.take(4)?.toIntOrNull()
            
            val updatedItem = item.copy(
                tmdbId = bestMatch.id,
                overview = bestMatch.overview,
                posterPath = bestMatch.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" } ?: item.posterPath,
                backdropPath = bestMatch.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                voteAverage = bestMatch.vote_average,
                releaseYear = item.releaseYear ?: extractedYear,
                isTmdbSyncAttempted = true
            )
            mediaDao.insertMediaItems(listOf(updatedItem))
        } else {
            Log.d("TmdbSync", "No match found for movie: ${item.title}")
            mediaDao.insertMediaItems(listOf(item.copy(isTmdbSyncAttempted = true)))
        }
    }

    private suspend fun syncTvShow(item: MediaItem, apiKey: String) {
        Log.d("TmdbSync", "Searching TV show: ${item.title}")

        val searchResponse = tmdbApi.searchTvShow(
            apiKey = apiKey,
            query = item.title,
            year = item.releaseYear
        )
        val bestShowMatch = searchResponse.results.firstOrNull()

        if (bestShowMatch == null) {
            Log.d("TmdbSync", "No match found for TV show: ${item.title}")
            mediaDao.insertMediaItems(listOf(item.copy(isTmdbSyncAttempted = true)))
            return
        }

        Log.d("TmdbSync", "Found match for TV show: ${item.title}: ${bestShowMatch.name} (ID: ${bestShowMatch.id})")


        val extractedYear = bestShowMatch.first_air_date?.take(4)?.toIntOrNull()


        val updatedShow = item.copy(
            tmdbId = bestShowMatch.id,
            overview = bestShowMatch.overview,
            posterPath = bestShowMatch.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" } ?: item.posterPath,
            backdropPath = bestShowMatch.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
            voteAverage = bestShowMatch.vote_average,
            releaseYear = item.releaseYear ?: extractedYear,
            isTmdbSyncAttempted = true
        )
        mediaDao.insertMediaItems(listOf(updatedShow))


        val localEpisodes = mediaDao.getEpisodesListForShow(item.id)
        if (localEpisodes.isEmpty()) return


        val seasonGroups = localEpisodes.groupBy { it.seasonNumber }
        val updatedEpisodes = mutableListOf<MediaEpisode>()

        for ((seasonNumber, episodesInSeason) in seasonGroups) {
            try {
                val seasonResponse = tmdbApi.getSeasonDetails(
                    tvId = bestShowMatch.id,
                    seasonNumber = seasonNumber,
                    apiKey = apiKey
                )


                for (localEp in episodesInSeason) {
                    val tmdbEp = seasonResponse.episodes.find { it.episode_number == localEp.episodeNumber }

                    if (tmdbEp != null) {
                        updatedEpisodes.add(
                            localEp.copy(
                                tmdbEpisodeId = tmdbEp.id,
                                episodeTitle = tmdbEp.name ?: localEp.episodeTitle,
                                episodeOverview = tmdbEp.overview,
                                stillPath = tmdbEp.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                            )
                        )
                    } else {
                        updatedEpisodes.add(localEp)
                    }
                }
            } catch (e: Exception) {
                Log.e("TmdbSync", "Failed to fetch Season $seasonNumber for ${item.title}", e)
                updatedEpisodes.addAll(episodesInSeason)
            }
        }


        if (updatedEpisodes.isNotEmpty()) {
            mediaDao.insertEpisodes(updatedEpisodes)
        }
    }
}