package com.calebms.openflix.data.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.calebms.openflix.data.local.AppDatabase
import com.calebms.openflix.data.local.entities.MediaEpisode
import com.calebms.openflix.data.local.entities.MediaItem
import com.calebms.openflix.data.parser.MediaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class MediaScanner(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val supportedExtensions = setOf("mp4", "mkv", "avi", "webm", "mov")
    private val supportedSubExtensions = setOf("srt", "vtt")

    suspend fun scanDirectory(treeUri: Uri) = withContext(Dispatchers.IO) {
        if (!android.provider.DocumentsContract.isTreeUri(treeUri)) return@withContext

        val rootDoc = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (e: IllegalArgumentException) {
            null
        } ?: return@withContext

        val existingMediaMap = db.mediaDao().getAllMediaList().associateBy { it.id }
        val existingEpisodesMap = db.mediaDao().getAllEpisodesList().associateBy { it.id }

        val movies = mutableListOf<MediaItem>()
        val tvShows = mutableMapOf<String, MediaItem>()
        val episodes = mutableListOf<MediaEpisode>()

        scanRecursive(rootDoc, movies, tvShows, episodes, existingMediaMap, existingEpisodesMap)

        db.mediaDao().insertMediaItems(movies + tvShows.values)
        db.mediaDao().insertEpisodes(episodes)
    }

    suspend fun verifyLibraryAvailability() = withContext(Dispatchers.IO) {
        val mediaItems = db.mediaDao().getAllMediaList()

        for (item in mediaItems) {
            val itemUri = item.localUri
            val itemFileName = try { Uri.parse(itemUri).lastPathSegment ?: "" } catch (e: Exception) { "" }
            if (itemUri.lowercase().contains("sample") || MediaParser.parse(itemFileName).isSample) {
                db.mediaDao().deleteMediaItem(item.id)
                continue
            }

            val exists = try {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(item.localUri))
                doc?.exists() == true
            } catch (e: Exception) {
                false
            }

            if (item.isAvailable != exists) {
                db.mediaDao().updateMediaAvailability(item.id, exists)
            }

            if (item.type == "TV_SHOW") {
                val episodes = db.mediaDao().getEpisodesListForShow(item.id)
                for (episode in episodes) {
                    val epUri = episode.localFileUri
                    val epFileName = try { Uri.parse(epUri).lastPathSegment ?: "" } catch (e: Exception) { "" }
                    if (epUri.lowercase().contains("sample") || MediaParser.parse(epFileName).isSample) {
                        db.mediaDao().deleteEpisode(episode.id)
                        continue
                    }

                    val epExists = try {
                        val doc = DocumentFile.fromSingleUri(context, Uri.parse(epUri))
                        doc?.exists() == true
                    } catch (e: Exception) {
                        false
                    }

                    if (episode.isAvailable != epExists) {
                        db.mediaDao().updateEpisodeAvailability(episode.id, epExists)
                    }
                }
            }
        }
    }

    private fun isSampleDirectory(dirName: String?): Boolean {
        if (dirName.isNullOrBlank()) return false
        val lower = dirName.lowercase()
        return lower == "sample" || lower == "samples" || lower.contains("sample")
    }

    private suspend fun scanRecursive(
        directory: DocumentFile,
        movies: MutableList<MediaItem>,
        tvShows: MutableMap<String, MediaItem>,
        episodes: MutableList<MediaEpisode>,
        existingMediaMap: Map<String, MediaItem>,
        existingEpisodesMap: Map<String, MediaEpisode>
    ) {
        val allFiles = directory.listFiles()

        val subtitleFiles = allFiles.filter { file ->
            val ext = file.name?.substringAfterLast(".", "")?.lowercase()
            ext in supportedSubExtensions
        }.associateBy { file ->
            file.name?.substringBeforeLast(".") ?: ""
        }

        allFiles.forEach { file ->
            if (file.isDirectory) {
                if (isSampleDirectory(file.name)) {
                    return@forEach
                }
                scanRecursive(file, movies, tvShows, episodes, existingMediaMap, existingEpisodesMap)
            } else if (file.isFile) {
                val ext = file.name?.substringAfterLast(".", "")?.lowercase()
                if (ext in supportedExtensions) {
                    val parsed = MediaParser.parse(file.name ?: "")
                    if (parsed.isSample) {
                        return@forEach
                    }

                    val baseName = file.name?.substringBeforeLast(".") ?: ""
                    val matchingSubtitle = subtitleFiles[baseName]?.uri?.toString()

                    if (parsed.isTvShow) {
                        val showKey = parsed.cleanTitle.lowercase()
                        val showId = tvShows[showKey]?.id
                            ?: existingMediaMap.values.find { it.type == "TV_SHOW" && it.title.lowercase() == showKey }?.id
                            ?: UUID.nameUUIDFromBytes(showKey.toByteArray()).toString()

                        val existingShow = existingMediaMap[showId]

                        if (!tvShows.containsKey(showKey)) {
                            val extraction = com.calebms.openflix.data.utils.ThumbnailUtils
                                .extractMediaInfo(context, file.uri, showId)

                            val posterToUse = if (existingShow?.posterPath != null && existingShow.posterPath.startsWith("http")) {
                                existingShow.posterPath
                            } else {
                                extraction.posterPath ?: existingShow?.posterPath
                            }

                            tvShows[showKey] = MediaItem(
                                id = showId,
                                title = existingShow?.title ?: parsed.cleanTitle,
                                type = "TV_SHOW",
                                releaseYear = parsed.releaseYear ?: existingShow?.releaseYear,
                                localUri = directory.uri.toString(),
                                posterPath = posterToUse,
                                overview = existingShow?.overview,
                                durationMs = extraction.durationMs ?: existingShow?.durationMs,
                                tmdbId = existingShow?.tmdbId,
                                backdropPath = existingShow?.backdropPath,
                                voteAverage = existingShow?.voteAverage,
                                isTmdbSyncAttempted = existingShow?.isTmdbSyncAttempted ?: false,
                                isAvailable = true,
                                subtitleUri = matchingSubtitle ?: existingShow?.subtitleUri,
                                genres = existingShow?.genres
                            )
                        } else if (tvShows[showKey]?.posterPath == null) {
                            val extraction = com.calebms.openflix.data.utils.ThumbnailUtils
                                .extractMediaInfo(context, file.uri, showId)
                            val posterToUse = extraction.posterPath ?: existingShow?.posterPath
                            if (posterToUse != null) {
                                tvShows[showKey] = tvShows[showKey]!!.copy(posterPath = posterToUse)
                            }
                        }

                        val epId = UUID.nameUUIDFromBytes(file.uri.toString().toByteArray()).toString()
                        val existingEp = existingEpisodesMap[epId]
                        val extraction = com.calebms.openflix.data.utils.ThumbnailUtils
                            .extractMediaInfo(context, file.uri, showId)

                        val stillToUse = if (existingEp?.stillPath != null && existingEp.stillPath.startsWith("http")) {
                            existingEp.stillPath
                        } else {
                            existingEp?.stillPath
                        }

                        episodes.add(
                            MediaEpisode(
                                id = epId,
                                mediaItemId = showId,
                                seasonNumber = parsed.seasonNumber ?: 1,
                                episodeNumber = parsed.episodeNumber ?: 1,
                                episodeTitle = existingEp?.episodeTitle ?: "Episode ${parsed.episodeNumber}",
                                localFileUri = file.uri.toString(),
                                durationMs = extraction.durationMs ?: existingEp?.durationMs,
                                tmdbEpisodeId = existingEp?.tmdbEpisodeId,
                                episodeOverview = existingEp?.episodeOverview,
                                stillPath = stillToUse,
                                isAvailable = true,
                                subtitleUri = matchingSubtitle ?: existingEp?.subtitleUri
                            )
                        )
                    } else {
                        val movieId = UUID.nameUUIDFromBytes(file.uri.toString().toByteArray()).toString()
                        val existingMovie = existingMediaMap[movieId]
                        val extraction = com.calebms.openflix.data.utils.ThumbnailUtils
                            .extractMediaInfo(context, file.uri, movieId)

                        val posterToUse = if (existingMovie?.posterPath != null && existingMovie.posterPath.startsWith("http")) {
                            existingMovie.posterPath
                        } else {
                            extraction.posterPath ?: existingMovie?.posterPath
                        }

                        movies.add(
                            MediaItem(
                                id = movieId,
                                title = existingMovie?.title ?: parsed.cleanTitle,
                                type = "MOVIE",
                                releaseYear = parsed.releaseYear ?: existingMovie?.releaseYear,
                                localUri = file.uri.toString(),
                                posterPath = posterToUse,
                                overview = existingMovie?.overview,
                                durationMs = extraction.durationMs ?: existingMovie?.durationMs,
                                tmdbId = existingMovie?.tmdbId,
                                backdropPath = existingMovie?.backdropPath,
                                voteAverage = existingMovie?.voteAverage,
                                isTmdbSyncAttempted = existingMovie?.isTmdbSyncAttempted ?: false,
                                isAvailable = true,
                                subtitleUri = matchingSubtitle ?: existingMovie?.subtitleUri,
                                genres = existingMovie?.genres
                            )
                        )
                    }
                }
            }
        }
    }
}
