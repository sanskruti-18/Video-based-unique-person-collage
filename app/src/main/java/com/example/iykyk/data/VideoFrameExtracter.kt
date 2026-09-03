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
        framesPerSecond: Int = 2
    ): List<Pair<Long, Bitmap>> = withContext(Dispatchers.IO) {// video processing happens in IO/background dispatcher

        val retriever = MediaMetadataRetriever() //opens the video
        val frames = mutableListOf<Pair<Long, Bitmap>>()

        try {
            retriever.setDataSource(context, videoUri)

            val durationMs = retriever // get the duration of the video
                .extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )
                ?.toLongOrNull()
                ?: 0L

            val intervalMs = 1000L / framesPerSecond

            var timestampMs = 0L

            while (timestampMs < durationMs) {

                //extracting the frame
                val bitmap = retriever.getFrameAtTime(
                    timestampMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (bitmap != null) {
                    frames.add(timestampMs to bitmap)
                }

                timestampMs += intervalMs
            }

        } finally {
            retriever.release()
        }

        frames
    }
}