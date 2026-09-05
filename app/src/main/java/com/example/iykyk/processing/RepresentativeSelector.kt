package com.example.iykyk.processing

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.iykyk.model.DetectedFace
import kotlin.math.abs

class RepresentativeSelector {

    companion object {
        private const val MIN_ACCEPTABLE_SHARPNESS = 0.10f
        private const val MULTI_FACE_PENALTY = 0.10f
    }

    fun selectBestFace(
        faces: List<DetectedFace>,
        faceCountAtTimestamp: Map<Long, Int> = emptyMap()
    ): DetectedFace? {

        if (faces.isEmpty()) {
            return null
        }

        Log.d(
            "IYKYK_REP",
            "Selecting representative from ${faces.size} candidates"
        )

        val scoredCandidates =
            faces.map { face ->

                val sharpness =
                    faceSharpnessScore(face)

                val facesInFrame =
                    faceCountAtTimestamp[
                        face.timestampMs
                    ] ?: 1

                val baseScore =
                    scoreFace(
                        face = face,
                        sharpness = sharpness
                    )

                /*
                 * Multiple faces are slightly penalized,
                 * but NOT rejected.
                 *
                 * A sharp multi-face frame is preferable to
                 * a completely blurred solo frame.
                 */
                val contextAdjustment =
                    if (facesInFrame <= 1) {
                        0f
                    } else {
                        -MULTI_FACE_PENALTY
                    }

                CandidateScore(
                    face = face,
                    sharpness = sharpness,
                    score =
                        baseScore +
                                contextAdjustment,
                    facesInFrame =
                        facesInFrame
                )
            }

        scoredCandidates.forEach { candidate ->

            Log.d(
                "IYKYK_REP",
                "timestamp=${candidate.face.timestampMs} " +
                        "sharpness=${"%.3f".format(candidate.sharpness)} " +
                        "score=${"%.3f".format(candidate.score)} " +
                        "facesInFrame=${candidate.facesInFrame}"
            )
        }

        /*
         * First use reasonably sharp candidates.
         */
        val acceptableCandidates =
            scoredCandidates.filter {
                it.sharpness >=
                        MIN_ACCEPTABLE_SHARPNESS
            }

        /*
         * If a good-quality frame exists, choose the highest
         * quality representative.
         */
        val best =
            if (acceptableCandidates.isNotEmpty()) {

                acceptableCandidates.maxWithOrNull(
                    compareBy<CandidateScore> {
                        it.score
                    }.thenBy {
                        it.sharpness
                    }
                )

            } else {

                /*
                 * Last resort:
                 * choose the sharpest frame available.
                 */
                scoredCandidates.maxWithOrNull(
                    compareBy<CandidateScore> {
                        it.sharpness
                    }.thenBy {
                        it.score
                    }
                )
            }

        if (best != null) {

            Log.d(
                "IYKYK_REP",
                "SELECTED timestamp=${best.face.timestampMs} " +
                        "sharpness=${"%.3f".format(best.sharpness)} " +
                        "score=${"%.3f".format(best.score)} " +
                        "facesInFrame=${best.facesInFrame}"
            )
        }

        return best?.face
    }

    private fun scoreFace(
        face: DetectedFace,
        sharpness: Float
    ): Float {

        val pose =
            poseScore(face)

        val eyes =
            eyesScore(face)

        val size =
            faceSizeScore(
                face.frame,
                face.boundingBox
            )

        val completeness =
            completenessScore(
                face.frame,
                face.boundingBox
            )

        val smile =
            face.smilingProbability ?: 0.5f

        return (
                sharpness * 0.50f +
                        pose * 0.20f +
                        eyes * 0.17f +
                        size * 0.08f +
                        completeness * 0.03f +
                        smile * 0.02f
                )
    }

    private fun poseScore(
        face: DetectedFace
    ): Float {

        val yaw =
            abs(face.headEulerAngleY)

        val pitch =
            abs(face.headEulerAngleX)

        val roll =
            abs(face.headEulerAngleZ)

        val yawScore =
            1f -
                    (yaw / 35f)
                        .coerceIn(0f, 1f)

        val pitchScore =
            1f -
                    (pitch / 35f)
                        .coerceIn(0f, 1f)

        val rollScore =
            1f -
                    (roll / 35f)
                        .coerceIn(0f, 1f)

        return (
                yawScore * 0.50f +
                        pitchScore * 0.30f +
                        rollScore * 0.20f
                )
    }

    private fun eyesScore(
        face: DetectedFace
    ): Float {

        val left =
            face.leftEyeOpenProbability

        val right =
            face.rightEyeOpenProbability

        if (
            left == null &&
            right == null
        ) {
            return 0.5f
        }

        if (left == null) {
            return right ?: 0.5f
        }

        if (right == null) {
            return left
        }

        return (
                left +
                        right
                ) / 2f
    }

    private fun faceSharpnessScore(
        face: DetectedFace
    ): Float {

        val bitmap =
            face.frame

        val box =
            face.boundingBox

        val marginX =
            (box.width() * 0.15f)
                .toInt()

        val marginY =
            (box.height() * 0.15f)
                .toInt()

        val left =
            (box.left - marginX)
                .coerceAtLeast(0)

        val top =
            (box.top - marginY)
                .coerceAtLeast(0)

        val right =
            (box.right + marginX)
                .coerceAtMost(
                    bitmap.width
                )

        val bottom =
            (box.bottom + marginY)
                .coerceAtMost(
                    bitmap.height
                )

        if (
            right <= left ||
            bottom <= top
        ) {
            return 0f
        }

        val crop =
            Bitmap.createBitmap(
                bitmap,
                left,
                top,
                right - left,
                bottom - top
            )

        return calculateSharpness(crop)
    }

    private fun calculateSharpness(
        bitmap: Bitmap
    ): Float {

        val maxDimension = 160

        val scale =
            minOf(
                1f,
                maxDimension.toFloat() /
                        maxOf(
                            bitmap.width,
                            bitmap.height
                        )
            )

        val width =
            (bitmap.width * scale)
                .toInt()
                .coerceAtLeast(3)

        val height =
            (bitmap.height * scale)
                .toInt()
                .coerceAtLeast(3)

        val small =
            if (
                width == bitmap.width &&
                height == bitmap.height
            ) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(
                    bitmap,
                    width,
                    height,
                    true
                )
            }

        val gray =
            IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {

                val pixel =
                    small.getPixel(x, y)

                val r =
                    (pixel shr 16) and 0xFF

                val g =
                    (pixel shr 8) and 0xFF

                val b =
                    pixel and 0xFF

                gray[y * width + x] =
                    (
                            299 * r +
                                    587 * g +
                                    114 * b
                            ) / 1000
            }
        }

        var sum = 0.0
        var sumSquared = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {

                val center =
                    gray[y * width + x]

                val top =
                    gray[(y - 1) * width + x]

                val bottom =
                    gray[(y + 1) * width + x]

                val left =
                    gray[y * width + x - 1]

                val right =
                    gray[y * width + x + 1]

                val laplacian =
                    (
                            top +
                                    bottom +
                                    left +
                                    right -
                                    4 * center
                            ).toDouble()

                sum += laplacian
                sumSquared +=
                    laplacian * laplacian

                count++
            }
        }

        if (count == 0) {
            return 0f
        }

        val mean =
            sum / count

        val variance =
            (sumSquared / count) -
                    mean * mean

        return (
                variance / 250.0
                )
            .coerceIn(
                0.0,
                1.0
            )
            .toFloat()
    }

    private fun faceSizeScore(
        bitmap: Bitmap,
        box: Rect
    ): Float {

        val frameArea =
            bitmap.width.toFloat() *
                    bitmap.height.toFloat()

        if (frameArea <= 0f) {
            return 0f
        }

        val faceArea =
            box.width().toFloat() *
                    box.height().toFloat()

        val ratio =
            faceArea / frameArea

        return when {
            ratio < 0.015f -> 0.2f
            ratio < 0.04f -> 0.6f
            ratio <= 0.20f -> 1f
            ratio <= 0.35f -> 0.8f
            else -> 0.5f
        }
    }

    private fun completenessScore(
        bitmap: Bitmap,
        box: Rect
    ): Float {

        val marginX =
            bitmap.width * 0.02f

        val marginY =
            bitmap.height * 0.02f

        var score = 0f

        if (box.left > marginX) score += 1f
        if (box.top > marginY) score += 1f

        if (
            box.right <
            bitmap.width - marginX
        ) {
            score += 1f
        }

        if (
            box.bottom <
            bitmap.height - marginY
        ) {
            score += 1f
        }

        return score / 4f
    }

    private data class CandidateScore(
        val face: DetectedFace,
        val sharpness: Float,
        val score: Float,
        val facesInFrame: Int
    )
}