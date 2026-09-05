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
         * Canonical MobileFaceNet 112x112 target landmarks.
         * Note ML Kit landmark conventions:
         * FaceLandmark.RIGHT_EYE is the subject's right eye, which is on the VIEWER'S LEFT side.
         * FaceLandmark.LEFT_EYE is the subject's left eye, which is on the VIEWER'S RIGHT side.
         */
        private val TARGET_VIEWER_LEFT_EYE =
            PointF(38.2946f, 51.6963f)

        private val TARGET_VIEWER_RIGHT_EYE =
            PointF(73.5318f, 51.5014f)

        private val TARGET_NOSE =
            PointF(56.0252f, 71.7366f)
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
                 * Normalize RGB: (pixel - 127.5) / 128.0 for MobileFaceNet
                 */
                input.putFloat(
                    (r - 127.5f) / 128.0f
                )

                input.putFloat(
                    (g - 127.5f) / 128.0f
                )

                input.putFloat(
                    (b - 127.5f) / 128.0f
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

        // ML Kit anatomical landmarks:
        // face.rightEyePosition is the subject's right eye (viewer's left)
        // face.leftEyePosition is the subject's left eye (viewer's right)
        val viewerLeftEye = face.rightEyePosition
        val viewerRightEye = face.leftEyePosition
        val nose = face.noseBasePosition

        if (
            viewerLeftEye != null &&
            viewerRightEye != null
        ) {

            val aligned =
                createAffineAlignedFace(
                    bitmap,
                    viewerLeftEye,
                    viewerRightEye,
                    nose
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
        viewerLeftEye: PointF,
        viewerRightEye: PointF,
        noseLandmark: PointF?
    ): Bitmap? {

        val dx =
            viewerRightEye.x - viewerLeftEye.x

        val dy =
            viewerRightEye.y - viewerLeftEye.y

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
            (viewerLeftEye.x +
                    viewerRightEye.x) / 2f

        val eyeCenterY =
            (viewerLeftEye.y +
                    viewerRightEye.y) / 2f

        /*
         * Vector perpendicular to the eye line directed downwards (towards nose/chin).
         * Since eye line goes from left eye to right eye (+X, dx > 0),
         * rotating 90 degrees clockwise gives (-dy, dx), which has +Y (downwards).
         */
        val perpendicularX =
            -dy

        val perpendicularY =
            dx

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

        val noseDistance =
            eyeDistance * 0.72f

        // Prefer actual nose landmark if available and reasonably positioned below eyes
        val sourceNose =
            if (noseLandmark != null && noseLandmark.y > eyeCenterY) {
                noseLandmark
            } else {
                PointF(
                    eyeCenterX +
                            normalizedX *
                            noseDistance,
                    eyeCenterY +
                            normalizedY *
                            noseDistance
                )
            }

        /*
         * Source points in the original video.
         */
        val sourcePoints =
            floatArrayOf(
                viewerLeftEye.x,
                viewerLeftEye.y,

                viewerRightEye.x,
                viewerRightEye.y,

                sourceNose.x,
                sourceNose.y
            )

        /*
         * Corresponding points in our fixed
         * 112x112 face coordinate system.
         */
        val destinationPoints =
            floatArrayOf(
                TARGET_VIEWER_LEFT_EYE.x,
                TARGET_VIEWER_LEFT_EYE.y,

                TARGET_VIEWER_RIGHT_EYE.x,
                TARGET_VIEWER_RIGHT_EYE.y,

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