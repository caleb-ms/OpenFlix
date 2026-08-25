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

    suspend fun scanDirectory(treeUri: Uri) = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext

        val movies = mutableListOf<MediaItem>()
        val tvShows = mutableMapOf<String, MediaItem>()
        val episodes = mutableListOf<MediaEpisode>()

        scanRecursive(rootDoc, movies, tvShows, episodes)


        db.mediaDao().insertMediaItems(movies + tvShows.values)
        db.mediaDao().insertEpisodes(episodes)
    }

    private suspend fun scanRecursive(
        directory: DocumentFile,
        movies: MutableList<MediaItem>,
        tvShows: MutableMap<String, MediaItem>,
        episodes: MutableList<MediaEpisode>
    ) {
        directory.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanRecursive(file, movies, tvShows, episodes)
            } else if (file.isFile) {
                val ext = file.name?.substringAfterLast(".", "")?.lowercase()
                if (ext in supportedExtensions) {
                    val parsed = MediaParser.parse(file.name ?: "")


                    if (parsed.isTvShow) {
                        val showKey = parsed.cleanTitle.lowercase()
                        val showId = tvShows[showKey]?.id ?: UUID.nameUUIDFromBytes(showKey.toByteArray()).toString()

                        if (!tvShows.containsKey(showKey)) {
                            tvShows[showKey] = MediaItem(
                                id = showId,
                                title = parsed.cleanTitle,
                                type = "TV_SHOW",
                                releaseYear = parsed.releaseYear,
                                localUri = directory.uri.toString(),
                                posterPath = null
                            )
                        }


                        val extraction = com.calebms.openflix.data.utils.ThumbnailUtils
                            .extractMediaInfo(context, file.uri, showId)


                        if (tvShows[showKey]?.posterPath == null && extraction.posterPath != null) {
                            tvShows[showKey] = tvShows[showKey]!!.copy(posterPath = extraction.posterPath)
                        }

                        episodes.add(
                            MediaEpisode(
                                id = UUID.nameUUIDFromBytes(file.uri.toString().toByteArray()).toString(),
                                mediaItemId = showId,
                                seasonNumber = parsed.seasonNumber ?: 1,
                                episodeNumber = parsed.episodeNumber ?: 1,
                                episodeTitle = "Episode ${parsed.episodeNumber}",
                                localFileUri = file.uri.toString(),
                                durationMs = extraction.durationMs // SAVE THE DURATION
                            )
                        )
                    } else {
                        val movieId = UUID.nameUUIDFromBytes(file.uri.toString().toByteArray()).toString()


                        val extraction = com.calebms.openflix.data.utils.ThumbnailUtils
                            .extractMediaInfo(context, file.uri, movieId)

                        movies.add(
                            MediaItem(
                                id = movieId,
                                title = parsed.cleanTitle,
                                type = "MOVIE",
                                releaseYear = parsed.releaseYear,
                                localUri = file.uri.toString(),
                                posterPath = extraction.posterPath,
                                durationMs = extraction.durationMs
                            )
                        )
                    }
                }
            }
        }
    }
}