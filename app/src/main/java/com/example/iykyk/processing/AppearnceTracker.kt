package com.example.iykyk.processing

import android.graphics.Rect
import android.util.Log
import com.example.iykyk.model.FaceEmbedding
import kotlin.math.sqrt

class AppearanceTracker {

    companion object {

        /*
         * Video is sampled at 4 FPS = 250 ms/frame.
         */
        private const val MAX_GAP_MS = 500L

        /*
         * Spatial continuity.
         */
        private const val IOU_THRESHOLD = 0.05f
        private const val MAX_CENTER_DISTANCE = 0.65f

        /*
         * Identity sanity check.
         *
         * This is only used for tracking continuity.
         * Final identity grouping is performed by FaceClusterer.
         */
        private const val MIN_IDENTITY_SIMILARITY = 0.30f

        /*
         * Remove extremely short detector fragments.
         *
         * IMPORTANT:
         * Use OR here.
         *
         * A genuine appearance can occasionally have only a few
         * sampled frames but still span approximately one second.
         */
        private const val MIN_APPEARANCE_DURATION_MS = 1000L
        private const val MIN_FRAMES_PER_APPEARANCE = 3

        /*
         * Transition / whip-pan protection.
         *
         * A huge face with an extreme pose touching the frame edge
         * is usually a transition artifact rather than a person.
         */
        private const val EXTREME_YAW = 65f
        private const val EXTREME_PITCH = 55f
        private const val EXTREME_ROLL = 55f
        private const val HUGE_FACE_RATIO = 0.75f
    }

    private data class Track(
        val faces: MutableList<FaceEmbedding> = mutableListOf()
    ) {

        val lastFace: FaceEmbedding
            get() = faces.last()

        val lastTimestamp: Long
            get() = lastFace.face.timestampMs

        fun add(face: FaceEmbedding) {
            faces.add(face)
        }
    }

    fun createAppearances(
        faceEmbeddings: List<FaceEmbedding>
    ): List<List<FaceEmbedding>> {

        if (faceEmbeddings.isEmpty()) {
            return emptyList()
        }

        /*
         * Group faces belonging to the same sampled frame.
         */
        val groupedByTimestamp =
            faceEmbeddings
                .groupBy {
                    it.face.timestampMs
                }
                .toSortedMap()

        val activeTracks =
            mutableListOf<Track>()

        val finishedTracks =
            mutableListOf<Track>()

        for ((timestamp, facesAtTimestamp) in groupedByTimestamp) {

            /*
             * -----------------------------------------------------
             * STEP 1 — EXPIRE OLD TRACKS
             * -----------------------------------------------------
             */
            val expiredTracks =
                activeTracks.filter {
                    timestamp - it.lastTimestamp >
                            MAX_GAP_MS
                }

            for (track in expiredTracks) {
                finishedTracks.add(track)
            }

            activeTracks.removeAll(expiredTracks)

            /*
 * ---------------------------------------------------------
 * STEP 2 — PROTECT AGAINST EXTREME TRANSITION ARTIFACTS
 * ---------------------------------------------------------
 *
 * We do NOT blindly remove an extreme-pose detection.
 *
 * If the detection still matches an already active track,
 * it is probably the same real person moving/turning quickly.
 *
 * Only reject an extreme detection when it:
 *
 * 1. looks like a transition artifact AND
 * 2. cannot be matched to an existing track.
 *
 * This is important for the 21000–21500 ms appearance.
 */
            val usableFaces =
                facesAtTimestamp.filter { candidate ->

                    val face =
                        candidate.face

                    val box =
                        face.boundingBox

                    val frameWidth =
                        face.frame.width.toFloat()

                    val frameHeight =
                        face.frame.height.toFloat()

                    if (
                        frameWidth <= 0f ||
                        frameHeight <= 0f
                    ) {
                        return@filter false
                    }

                    val yaw =
                        kotlin.math.abs(
                            face.headEulerAngleY
                        )

                    val pitch =
                        kotlin.math.abs(
                            face.headEulerAngleX
                        )

                    val roll =
                        kotlin.math.abs(
                            face.headEulerAngleZ
                        )

                    val hugeFace =
                        box.width().toFloat() /
                                frameWidth >
                                HUGE_FACE_RATIO ||
                                box.height().toFloat() /
                                frameHeight >
                                HUGE_FACE_RATIO

                    val extremePose =
                        yaw > EXTREME_YAW ||
                                pitch > EXTREME_PITCH ||
                                roll > EXTREME_ROLL

                    val touchesEdge =
                        box.left <= frameWidth * 0.015f ||
                                box.top <= frameHeight * 0.015f ||
                                box.right >= frameWidth * 0.985f ||
                                box.bottom >= frameHeight * 0.985f

                    val possibleTransition =
                        hugeFace &&
                                extremePose &&
                                touchesEdge

                    /*
                     * If it doesn't look like a transition,
                     * keep it normally.
                     */
                    if (!possibleTransition) {
                        return@filter true
                    }

                    /*
                     * -----------------------------------------------------
                     * Check whether this "bad-looking" detection actually
                     * continues an existing person.
                     * -----------------------------------------------------
                     */
                    val continuesExistingTrack =
                        activeTracks.any { track ->

                            val previous =
                                track.lastFace

                            val previousBox =
                                previous.face.boundingBox

                            val gap =
                                timestamp -
                                        previous.face.timestampMs

                            if (gap > MAX_GAP_MS) {
                                return@any false
                            }

                            val iou =
                                calculateIoU(
                                    previousBox,
                                    box
                                )

                            val centerDistance =
                                normalizedCenterDistance(
                                    previousBox,
                                    box
                                )

                            val spatiallyRelated =
                                iou >= IOU_THRESHOLD ||
                                        centerDistance <=
                                        MAX_CENTER_DISTANCE

                            if (!spatiallyRelated) {
                                return@any false
                            }

                            val identitySimilarity =
                                cosineSimilarity(
                                    previous.embedding,
                                    candidate.embedding
                                )

                            identitySimilarity >=
                                    MIN_IDENTITY_SIMILARITY
                        }

                    if (continuesExistingTrack) {

                        /*
                         * This is a real tracked face despite the
                         * extreme pose/bounding box.
                         *
                         * KEEP IT.
                         */
                        Log.d(
                            "IYKYK_TRACK",
                            "Keeping extreme detection because it " +
                                    "continues an existing track: " +
                                    "timestamp=$timestamp " +
                                    "yaw=${"%.1f".format(yaw)} " +
                                    "bbox=$box"
                        )

                        true

                    } else {

                        /*
                         * No existing identity supports this detection.
                         * It is much more likely to be a transition artifact.
                         */
                        Log.d(
                            "IYKYK_TRACK",
                            "Rejecting untracked transition artifact: " +
                                    "timestamp=$timestamp " +
                                    "yaw=${"%.1f".format(yaw)} " +
                                    "pitch=${"%.1f".format(pitch)} " +
                                    "roll=${"%.1f".format(roll)} " +
                                    "bbox=$box"
                        )

                        false
                    }
                }

            /*
             * -----------------------------------------------------
             * STEP 3 — MATCH CURRENT FACES TO TRACKS
             * -----------------------------------------------------
             *
             * IMPORTANT:
             *
             * We deliberately do NOT perform aggressive same-frame
             * bounding-box containment filtering here.
             *
             * Two clearly visible people can legitimately occupy
             * overlapping regions of a frame.
             */
            val currentFaces =
                usableFaces.sortedByDescending {
                    faceArea(
                        it.face.boundingBox
                    )
                }

            /*
             * A track can only be used once for a given timestamp.
             */
            val usedTracks =
                mutableSetOf<Track>()

            for (currentFace in currentFaces) {

                var bestTrack: Track? = null
                var bestScore =
                    Float.MAX_VALUE

                for (track in activeTracks) {

                    if (track in usedTracks) {
                        continue
                    }

                    val previousFace =
                        track.lastFace

                    val gap =
                        timestamp -
                                previousFace.face.timestampMs

                    if (gap > MAX_GAP_MS) {
                        continue
                    }

                    val previousBox =
                        previousFace.face.boundingBox

                    val currentBox =
                        currentFace.face.boundingBox

                    /*
                     * -----------------------------
                     * Spatial similarity
                     * -----------------------------
                     */
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

                    val spatialMatch =
                        iou >= IOU_THRESHOLD ||
                                centerDistance <=
                                MAX_CENTER_DISTANCE

                    if (!spatialMatch) {
                        continue
                    }

                    /*
                     * -----------------------------
                     * Identity similarity
                     * -----------------------------
                     */
                    val identitySimilarity =
                        trackIdentitySimilarity(
                            track,
                            currentFace
                        )

                    /*
                     * Reject obviously different identities
                     * even when their boxes are close.
                     */
                    if (
                        identitySimilarity <
                        MIN_IDENTITY_SIMILARITY
                    ) {
                        continue
                    }

                    /*
                     * Lower score = better match.
                     *
                     * Spatial continuity is primary.
                     * Identity is a secondary sanity check.
                     */
                    val spatialScore =
                        if (
                            iou >=
                            IOU_THRESHOLD
                        ) {

                            1f - iou

                        } else {

                            1f +
                                    centerDistance
                        }

                    val identityPenalty =
                        1f -
                                identitySimilarity

                    val score =
                        spatialScore +
                                identityPenalty *
                                0.15f

                    if (
                        score <
                        bestScore
                    ) {

                        bestScore =
                            score

                        bestTrack =
                            track
                    }
                }

                /*
                 * -----------------------------
                 * Add to existing track
                 * -----------------------------
                 */
                if (bestTrack != null) {

                    bestTrack.add(
                        currentFace
                    )

                    usedTracks.add(
                        bestTrack
                    )

                } else {

                    /*
                     * -----------------------------
                     * Start a new appearance
                     * -----------------------------
                     */
                    val newTrack =
                        Track(
                            faces =
                                mutableListOf(
                                    currentFace
                                )
                        )

                    activeTracks.add(
                        newTrack
                    )

                    usedTracks.add(
                        newTrack
                    )
                }
            }
        }

        /*
         * -----------------------------------------------------
         * STEP 4 — FINISH REMAINING TRACKS
         * -----------------------------------------------------
         */
        finishedTracks.addAll(
            activeTracks
        )

        /*
         * -----------------------------------------------------
         * STEP 5 — SORT CHRONOLOGICALLY
         * -----------------------------------------------------
         */
        val sortedTracks =
            finishedTracks
                .filter {
                    it.faces.isNotEmpty()
                }
                .sortedBy {
                    it.faces
                        .first()
                        .face
                        .timestampMs
                }

        /*
         * -----------------------------------------------------
         * STEP 6 — REMOVE ONLY VERY SHORT FALSE TRACKS
         * -----------------------------------------------------
         *
         * IMPORTANT:
         *
         * Keep OR rather than AND.
         *
         * This is the behavior that preserves legitimate
         * appearances while removing tiny detector fragments.
         */
        val validTracks =
            sortedTracks.filter { track ->

                val start =
                    track.faces
                        .first()
                        .face
                        .timestampMs

                val end =
                    track.faces
                        .last()
                        .face
                        .timestampMs

                val duration =
                    end - start

                val enoughFrames =
                    track.faces.size >=
                            MIN_FRAMES_PER_APPEARANCE

                val longEnough =
                    duration >=
                            MIN_APPEARANCE_DURATION_MS

                val valid =
                    enoughFrames ||
                            longEnough

                if (!valid) {

                    Log.d(
                        "IYKYK_TRACK",
                        "Discarding short detector fragment: " +
                                "frames=${track.faces.size} " +
                                "duration=${duration}ms " +
                                "start=$start " +
                                "end=$end"
                    )
                }

                valid
            }

        /*
         * -----------------------------------------------------
         * DEBUG
         * -----------------------------------------------------
         */
        Log.d(
            "IYKYK_TRACK",
            "Total valid appearances = " +
                    validTracks.size
        )

        validTracks.forEachIndexed {
                index,
                track ->

            val start =
                track.faces
                    .first()
                    .face
                    .timestampMs

            val end =
                track.faces
                    .last()
                    .face
                    .timestampMs

            Log.d(
                "IYKYK_TRACK",
                "Appearance ${index + 1}: " +
                        "${track.faces.size} frames, " +
                        "$start-$end ms"
            )
        }

        return validTracks.map {
            it.faces
        }
    }

    private fun trackIdentitySimilarity(
        track: Track,
        current: FaceEmbedding
    ): Float {

        val recentFaces =
            track.faces
                .takeLast(3)

        var bestSimilarity =
            -1f

        for (face in recentFaces) {

            val similarity =
                cosineSimilarity(
                    face.embedding,
                    current.embedding
                )

            if (
                similarity >
                bestSimilarity
            ) {
                bestSimilarity =
                    similarity
            }
        }

        return bestSimilarity
    }

    private fun faceArea(
        rect: Rect
    ): Int {

        return rect.width() *
                rect.height()
    }

    private fun calculateIoU(
        a: Rect,
        b: Rect
    ): Float {

        val left =
            maxOf(
                a.left,
                b.left
            )

        val top =
            maxOf(
                a.top,
                b.top
            )

        val right =
            minOf(
                a.right,
                b.right
            )

        val bottom =
            minOf(
                a.bottom,
                b.bottom
            )

        if (
            right <= left ||
            bottom <= top
        ) {
            return 0f
        }

        val intersection =
            (right - left) *
                    (bottom - top)

        val areaA =
            a.width() *
                    a.height()

        val areaB =
            b.width() *
                    b.height()

        val union =
            areaA +
                    areaB -
                    intersection

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

        val referenceSize =
            maxOf(
                a.width(),
                a.height(),
                1
            ).toFloat()

        return distance /
                referenceSize
    }

    private fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray
    ): Float {

        if (
            a.size != b.size ||
            a.isEmpty()
        ) {
            return -1f
        }

        var dot = 0f
        var magnitudeA = 0f
        var magnitudeB = 0f

        for (i in a.indices) {

            dot +=
                a[i] * b[i]

            magnitudeA +=
                a[i] * a[i]

            magnitudeB +=
                b[i] * b[i]
        }

        if (
            magnitudeA <= 0f ||
            magnitudeB <= 0f
        ) {
            return -1f
        }

        return dot /
                (
                        sqrt(magnitudeA) *
                                sqrt(magnitudeB)
                        )
    }
}