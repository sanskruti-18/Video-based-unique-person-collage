package com.example.iykyk.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import com.example.iykyk.model.DetectedFace
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

class FaceEmbedder(context: Context) {

    private val interpreter: Interpreter

    companion object {
        private const val MODEL_NAME = "mobilefacenet.tflite"

        private const val INPUT_SIZE = 112
        private const val EMBEDDING_SIZE = 192

        /*
         * Canonical eye positions.
         *
         * MobileFaceNet receives every face in the
         * same normalized geometric arrangement.
         */
        private val TARGET_LEFT_EYE =
            PointF(35f, 45f)

        private val TARGET_RIGHT_EYE =
            PointF(77f, 45f)

        /*
         * Synthetic nose/chin-direction point.
         *
         * This gives us a third point so that
         * Matrix.setPolyToPoly() can perform an
         * affine transformation.
         */
        private val TARGET_NOSE =
            PointF(56f, 75f)
    }

    init {
        val model =
            context.assets
                .open(MODEL_NAME)
                .use { it.readBytes() }

        val buffer =
            ByteBuffer
                .allocateDirect(model.size)
                .order(ByteOrder.nativeOrder())

        buffer.put(model)
        buffer.rewind()

        interpreter =
            Interpreter(buffer)
    }

    fun getEmbedding(
        face: DetectedFace
    ): FloatArray {

        val alignedFace =
            alignFace(face)

        val resized =
            if (
                alignedFace.width != INPUT_SIZE ||
                alignedFace.height != INPUT_SIZE
            ) {
                Bitmap.createScaledBitmap(
                    alignedFace,
                    INPUT_SIZE,
                    INPUT_SIZE,
                    true
                )
            } else {
                alignedFace
            }

        val input =
            ByteBuffer
                .allocateDirect(
                    INPUT_SIZE *
                            INPUT_SIZE *
                            3 *
                            4
                )
                .order(ByteOrder.nativeOrder())

        for (y in 0 until INPUT_SIZE) {

            for (x in 0 until INPUT_SIZE) {

                val pixel =
                    resized.getPixel(x, y)

                val r =
                    (pixel shr 16) and 0xFF

                val g =
                    (pixel shr 8) and 0xFF

                val b =
                    pixel and 0xFF

                /*
                 * Normalize RGB from [0,255]
                 * to approximately [-1,1].
                 */
                input.putFloat(
                    (r / 128.0f) - 1.0f
                )

                input.putFloat(
                    (g / 128.0f) - 1.0f
                )

                input.putFloat(
                    (b / 128.0f) - 1.0f
                )
            }
        }

        input.rewind()

        val output =
            Array(1) {
                FloatArray(
                    EMBEDDING_SIZE
                )
            }

        interpreter.run(
            input,
            output
        )

        return l2Normalize(
            output[0]
        )
    }

    /**
     * Aligns the detected face to a fixed 112x112
     * coordinate system.
     *
     * We use:
     *
     *   left eye
     *   right eye
     *   synthetic nose direction
     *
     * to calculate an affine transformation.
     */
    private fun alignFace(
        face: DetectedFace
    ): Bitmap {

        val bitmap = face.frame

        val leftEye = face.leftEyePosition
        val rightEye = face.rightEyePosition

        if (
            leftEye != null &&
            rightEye != null
        ) {

            val aligned =
                createAffineAlignedFace(
                    bitmap,
                    leftEye,
                    rightEye
                )

            if (aligned != null) {
                return aligned
            }
        }

        return cropFaceSquare(
            bitmap,
            face.boundingBox
        )
    }

    private fun createAffineAlignedFace(
        bitmap: Bitmap,
        leftEye: PointF,
        rightEye: PointF
    ): Bitmap? {

        val dx =
            rightEye.x - leftEye.x

        val dy =
            rightEye.y - leftEye.y

        val eyeDistance =
            sqrt(
                dx * dx +
                        dy * dy
            )

        /*
         * Eyes that are too close together are
         * unreliable for alignment.
         */
        if (eyeDistance < 5f) {
            return null
        }

        /*
         * Midpoint between the eyes.
         */
        val eyeCenterX =
            (leftEye.x +
                    rightEye.x) / 2f

        val eyeCenterY =
            (leftEye.y +
                    rightEye.y) / 2f

        /*
         * Vector perpendicular to the eye line.
         *
         * For a normal horizontal eye line:
         *
         *       left ---- right
         *              |
         *              ↓
         *            nose
         */
        val perpendicularX =
            -dy

        val perpendicularY =
            dx

        /*
         * Normalize the perpendicular vector.
         */
        val perpendicularLength =
            sqrt(
                perpendicularX *
                        perpendicularX +
                        perpendicularY *
                        perpendicularY
            )

        if (perpendicularLength < 0.001f) {
            return null
        }

        val normalizedX =
            perpendicularX /
                    perpendicularLength

        val normalizedY =
            perpendicularY /
                    perpendicularLength

        /*
         * Create a synthetic point below the
         * eye midpoint.
         *
         * It is not an actual nose landmark.
         * It only establishes the face's vertical
         * orientation for the affine transform.
         */
        val noseDistance =
            eyeDistance * 0.72f

        val sourceNose =
            PointF(
                eyeCenterX +
                        normalizedX *
                        noseDistance,

                eyeCenterY +
                        normalizedY *
                        noseDistance
            )

        /*
         * Source points in the original video.
         */
        val sourcePoints =
            floatArrayOf(
                leftEye.x,
                leftEye.y,

                rightEye.x,
                rightEye.y,

                sourceNose.x,
                sourceNose.y
            )

        /*
         * Corresponding points in our fixed
         * 112x112 face coordinate system.
         */
        val destinationPoints =
            floatArrayOf(
                TARGET_LEFT_EYE.x,
                TARGET_LEFT_EYE.y,

                TARGET_RIGHT_EYE.x,
                TARGET_RIGHT_EYE.y,

                TARGET_NOSE.x,
                TARGET_NOSE.y
            )

        val matrix =
            Matrix()

        val success =
            matrix.setPolyToPoly(
                sourcePoints,
                0,
                destinationPoints,
                0,
                3
            )

        if (!success) {
            return null
        }

        /*
         * Draw the original video frame through
         * the affine transformation into exactly
         * 112x112 pixels.
         */
        val output =
            Bitmap.createBitmap(
                INPUT_SIZE,
                INPUT_SIZE,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(output)

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG)

        canvas.drawBitmap(
            bitmap,
            matrix,
            paint
        )

        return output
    }

    /**
     * Fallback crop used when eye landmarks
     * are unavailable.
     */
    private fun cropFaceSquare(
        bitmap: Bitmap,
        box: Rect
    ): Bitmap {

        val marginX =
            (box.width() * 0.30f)
                .toInt()

        val marginY =
            (box.height() * 0.40f)
                .toInt()

        val expandedLeft =
            box.left - marginX

        val expandedTop =
            box.top - marginY

        val expandedRight =
            box.right + marginX

        val expandedBottom =
            box.bottom + marginY

        val expandedWidth =
            expandedRight -
                    expandedLeft

        val expandedHeight =
            expandedBottom -
                    expandedTop

        val cropSize =
            max(
                expandedWidth,
                expandedHeight
            )

        var left =
            expandedLeft -
                    (cropSize -
                            expandedWidth) / 2

        var top =
            expandedTop -
                    (cropSize -
                            expandedHeight) / 2

        left =
            left.coerceIn(
                0,
                max(
                    0,
                    bitmap.width -
                            cropSize
                )
            )

        top =
            top.coerceIn(
                0,
                max(
                    0,
                    bitmap.height -
                            cropSize
                )
            )

        val safeCropSize =
            minOf(
                cropSize,
                bitmap.width - left,
                bitmap.height - top
            ).coerceAtLeast(1)

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            safeCropSize,
            safeCropSize
        )
    }

    private fun l2Normalize(
        embedding: FloatArray
    ): FloatArray {

        var sum = 0.0f

        for (value in embedding) {
            sum += value * value
        }

        val magnitude =
            sqrt(sum)

        if (magnitude == 0f) {
            return embedding
        }

        return FloatArray(
            embedding.size
        ) { index ->
            embedding[index] /
                    magnitude
        }
    }

    fun close() {
        interpreter.close()
    }
}