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
}