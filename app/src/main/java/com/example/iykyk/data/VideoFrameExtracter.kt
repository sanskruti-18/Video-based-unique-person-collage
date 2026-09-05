package com.example.iykyk.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoFrameExtractor(
    private val context: Context
) {

    suspend fun extractFrames(
        videoUri: Uri,
        framesPerSecond: Int = 4
    ): List<Pair<Long, Bitmap>> = withContext(Dispatchers.IO) {

        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Pair<Long, Bitmap>>()

        try {
            retriever.setDataSource(context, videoUri)

            val durationMs = retriever
                .extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )
                ?.toLongOrNull()
                ?: 0L

            val intervalMs = 1000L / framesPerSecond
            var timestampMs = 0L

            val maxDimension = 1280

            while (timestampMs < durationMs) {

                val rawBitmap = retriever.getFrameAtTime(
                    timestampMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (rawBitmap != null) {
                    val processedBitmap = if (rawBitmap.width > maxDimension || rawBitmap.height > maxDimension) {
                        val scale = maxDimension.toFloat() / maxOf(rawBitmap.width, rawBitmap.height)
                        val targetW = (rawBitmap.width * scale).toInt().coerceAtLeast(1)
                        val targetH = (rawBitmap.height * scale).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
                        if (scaled !== rawBitmap) {
                            rawBitmap.recycle()
                        }
                        scaled
                    } else {
                        rawBitmap
                    }
                    frames.add(timestampMs to processedBitmap)
                }

                timestampMs += intervalMs
            }

        } finally {
            retriever.release()
        }

        frames
    }
}