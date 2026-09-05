package com.example.iykyk.processing

import android.graphics.Rect
import com.example.iykyk.model.FaceEmbedding
import kotlin.math.sqrt

class AppearanceTracker {

    companion object {

        // Video is sampled every 250 ms (4 fps).
        // A gap of up to 1000 ms bridges momentary head turns or blinks.
        private const val MAX_GAP_MS = 1000L

        // Minimum overlap between consecutive face boxes.
        private const val IOU_THRESHOLD = 0.05f

        // Maximum allowed movement relative to previous face size.
        private const val MAX_CENTER_DISTANCE = 0.65f

        // Minimum duration for a valid appearance (filters whip-pans)
        private const val MIN_APPEARANCE_DURATION_MS = 400L
        private const val MIN_FRAMES_PER_APPEARANCE = 2
    }

    private data class Track(
        val faces: MutableList<FaceEmbedding>,
        var lastTimestampMs: Long
    )

    fun createAppearances(
        faces: List<FaceEmbedding>
    ): List<List<FaceEmbedding>> {

        if (faces.isEmpty()) {
            return emptyList()
        }

        val sortedFaces =
            faces.sortedBy { it.face.timestampMs }

        val activeTracks =
            mutableListOf<Track>()

        val completedTracks =
            mutableListOf<List<FaceEmbedding>>()

        /*
         * Process faces frame-by-frame.
         *
         * We group faces having the same timestamp together
         * so one track can never consume two faces from
         * the same video frame.
         */
        val frames =
            sortedFaces.groupBy {
                it.face.timestampMs
            }

        for ((timestamp, facesAtTimestamp) in frames) {

            // Close tracks that have disappeared.
            val expired =
                activeTracks.filter {
                    timestamp - it.lastTimestampMs >
                            MAX_GAP_MS
                }

            for (track in expired) {
                completedTracks.add(
                    track.faces.toList()
                )
            }

            activeTracks.removeAll(
                expired.toSet()
            )

            /*
             * A track can only be matched once per frame.
             */
            val usedTracks =
                mutableSetOf<Track>()

            /*
             * Larger faces first.
             */
            val currentFaces =
                facesAtTimestamp.sortedByDescending {

                    it.face.boundingBox.width() *
                            it.face.boundingBox.height()
                }

            for (current in currentFaces) {

                var bestTrack: Track? = null
                var bestScore = Float.MAX_VALUE

                for (track in activeTracks) {

                    if (track in usedTracks) {
                        continue
                    }

                    val previous =
                        track.faces.last()

                    val timeGap =
                        timestamp -
                                previous.face.timestampMs

                    if (timeGap > MAX_GAP_MS) {
                        continue
                    }

                    val previousBox =
                        previous.face.boundingBox

                    val currentBox =
                        current.face.boundingBox

                    val iou =
                        calculateIoU(
                            previousBox,
                            currentBox
                        )

                    val centerDistance =
                        normalizedCenterDistance(
                            previousBox,
                            currentBox
                        )

                    val isMatch =
                        iou >= IOU_THRESHOLD ||
                                centerDistance <=
                                MAX_CENTER_DISTANCE

                    if (!isMatch) {
                        continue
                    }

                    val score =
                        if (iou >= IOU_THRESHOLD) {
                            1f - iou
                        } else {
                            1f + centerDistance
                        }

                    if (score < bestScore) {
                        bestScore = score
                        bestTrack = track
                    }
                }

                if (bestTrack != null) {

                    bestTrack.faces.add(
                        current
                    )

                    bestTrack.lastTimestampMs =
                        timestamp

                    usedTracks.add(
                        bestTrack
                    )

                } else {

                    /*
                     * No nearby face from the previous frame.
                     * Start a new appearance.
                     */
                    activeTracks.add(
                        Track(
                            faces =
                                mutableListOf(current),
                            lastTimestampMs =
                                timestamp
                        )
                    )
                }
            }
        }

        /*
         * Finish all remaining tracks.
         */
        completedTracks.addAll(
            activeTracks.map {
                it.faces.toList()
            }
        )

        /*
         * Filter out spurious whip-pan passes:
         * An appearance must be a continuous visible segment,
         * not a 1-frame blurred whip-pan pass.
         */
        return completedTracks.filter { track ->
            val duration = (track.last().face.timestampMs - track.first().face.timestampMs)
            track.size >= MIN_FRAMES_PER_APPEARANCE || duration >= MIN_APPEARANCE_DURATION_MS
        }
    }

    private fun calculateIoU(
        a: Rect,
        b: Rect
    ): Float {

        val left =
            maxOf(a.left, b.left)

        val top =
            maxOf(a.top, b.top)

        val right =
            minOf(a.right, b.right)

        val bottom =
            minOf(a.bottom, b.bottom)

        val width =
            (right - left).coerceAtLeast(0)

        val height =
            (bottom - top).coerceAtLeast(0)

        val intersection =
            width * height

        if (intersection <= 0) {
            return 0f
        }

        val areaA =
            a.width() * a.height()

        val areaB =
            b.width() * b.height()

        val union =
            areaA + areaB - intersection

        if (union <= 0) {
            return 0f
        }

        return intersection.toFloat() /
                union.toFloat()
    }

    private fun normalizedCenterDistance(
        a: Rect,
        b: Rect
    ): Float {

        val centerAX =
            (a.left + a.right) / 2f

        val centerAY =
            (a.top + a.bottom) / 2f

        val centerBX =
            (b.left + b.right) / 2f

        val centerBY =
            (b.top + b.bottom) / 2f

        val dx =
            centerAX - centerBX

        val dy =
            centerAY - centerBY

        val distance =
            sqrt(
                dx * dx +
                        dy * dy
            )

        /*
         * Normalize movement by the previous face size.
         * This makes the threshold work for both near and
         * far faces.
         */
        val referenceSize =
            maxOf(
                a.width(),
                a.height(),
                1
            ).toFloat()

        return distance / referenceSize
    }
}