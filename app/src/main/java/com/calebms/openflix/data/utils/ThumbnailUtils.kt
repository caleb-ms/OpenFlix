package com.calebms.openflix.data.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


data class MediaExtractionResult(
    val posterPath: String?,
    val durationMs: Long?
)

object ThumbnailUtils {

    suspend fun extractMediaInfo(
        context: Context,
        videoUri: Uri,
        mediaId: String
    ): MediaExtractionResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var thumbPath: String? = null
        var duration: Long? = null

        try {
            retriever.setDataSource(context, videoUri)


            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = durationStr?.toLongOrNull()


            val bitmap = retriever.getFrameAtTime(
                300_000_000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            bitmap?.let {
                val thumbFile = File(context.cacheDir, "thumb_$mediaId.jpg")
                FileOutputStream(thumbFile).use { out ->
                    it.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                thumbPath = thumbFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return@withContext MediaExtractionResult(thumbPath, duration)
    }

    suspend fun cachePermanentHistoryThumbnail(
        context: Context,
        videoUriString: String,
        historyIdKey: String
    ): String? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var savedPath: String? = null

        try {
            val uri = Uri.parse(videoUriString)
            if (uri.scheme == "content") {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.use {
                    retriever.setDataSource(it.fileDescriptor)
                }
            } else {
                retriever.setDataSource(context, uri)
            }

            val bitmap = retriever.getFrameAtTime(
                300_000_000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            bitmap?.let {
                val dir = File(context.filesDir, "watch_history_thumbs")
                if (!dir.exists()) dir.mkdirs()

                val thumbFile = File(dir, "thumb_$historyIdKey.jpg")
                FileOutputStream(thumbFile).use { out ->
                    it.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                savedPath = thumbFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return@withContext savedPath
    }
}