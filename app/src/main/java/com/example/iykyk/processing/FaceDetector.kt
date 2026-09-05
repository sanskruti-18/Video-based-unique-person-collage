package com.example.iykyk.processing

import android.graphics.Bitmap
import android.util.Log
import com.example.iykyk.model.DetectedFace
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.tasks.await

class FaceDetector {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(
                FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
            )
            .setLandmarkMode(
                FaceDetectorOptions.LANDMARK_MODE_ALL
            )
            .setClassificationMode(
                FaceDetectorOptions.CLASSIFICATION_MODE_ALL
            )
            .setMinFaceSize(0.06f)
            .build()
    )

    suspend fun detectFaces(
        timestampMs: Long,
        bitmap: Bitmap
    ): List<DetectedFace> {

        val image =
            InputImage.fromBitmap(bitmap, 0)

        val faces =
            detector.process(image).await()

        /*
         * ---------------------------------------------------------
         * DEBUG LOGGING
         * ---------------------------------------------------------
         *
         * This tells us exactly what ML Kit detected in every
         * sampled frame.
         *
         * We are especially interested in:
         *
         * 21000 ms
         * 21250 ms
         * 21500 ms
         *
         * because A17 comes from this region.
         */
        Log.d(
            "IYKYK_FACE",
            "timestamp=$timestampMs " +
                    "detectedFaces=${faces.size} " +
                    "frame=${bitmap.width}x${bitmap.height}"
        )

        faces.forEachIndexed { index, face ->

            val box =
                face.boundingBox

            val leftEye =
                face.getLandmark(
                    FaceLandmark.LEFT_EYE
                )?.position

            val rightEye =
                face.getLandmark(
                    FaceLandmark.RIGHT_EYE
                )?.position

            val noseBase =
                face.getLandmark(
                    FaceLandmark.NOSE_BASE
                )?.position

            /*
             * Detailed information for every detected face.
             */
            Log.d(
                "IYKYK_FACE",
                "  Face[$index] " +
                        "bbox=(${box.left},${box.top})-" +
                        "(${box.right},${box.bottom}) " +
                        "size=${box.width()}x${box.height()} " +
                        "yaw=${"%.1f".format(face.headEulerAngleY)} " +
                        "pitch=${"%.1f".format(face.headEulerAngleX)} " +
                        "roll=${"%.1f".format(face.headEulerAngleZ)} " +
                        "leftEye=${leftEye != null} " +
                        "rightEye=${rightEye != null} " +
                        "nose=${noseBase != null} " +
                        "leftOpen=${face.leftEyeOpenProbability?.let { "%.2f".format(it) }}" +
                        " rightOpen=${face.rightEyeOpenProbability?.let { "%.2f".format(it) }}" +
                        " smile=${face.smilingProbability?.let { "%.2f".format(it) }}"
            )
        }

        /*
         * ---------------------------------------------------------
         * SPECIAL DIAGNOSTIC FOR THE PROBLEMATIC A17 REGION
         * ---------------------------------------------------------
         */
        if (
            timestampMs in 20750L..21750L
        ) {

            Log.d(
                "IYKYK_A17",
                "========== A17 REGION =========="
            )

            Log.d(
                "IYKYK_A17",
                "timestamp=$timestampMs"
            )

            Log.d(
                "IYKYK_A17",
                "ML Kit detected ${faces.size} face(s)"
            )

            faces.forEachIndexed { index, face ->

                val box =
                    face.boundingBox

                Log.d(
                    "IYKYK_A17",
                    "Face[$index]: " +
                            "left=${box.left}, " +
                            "top=${box.top}, " +
                            "right=${box.right}, " +
                            "bottom=${box.bottom}, " +
                            "width=${box.width()}, " +
                            "height=${box.height()}, " +
                            "area=${box.width() * box.height()}, " +
                            "yaw=${"%.1f".format(face.headEulerAngleY)}, " +
                            "pitch=${"%.1f".format(face.headEulerAngleX)}, " +
                            "roll=${"%.1f".format(face.headEulerAngleZ)}"
                )
            }

            Log.d(
                "IYKYK_A17",
                "================================"
            )
        }

        return faces.map { face ->

            val leftEye =
                face.getLandmark(
                    FaceLandmark.LEFT_EYE
                )?.position

            val rightEye =
                face.getLandmark(
                    FaceLandmark.RIGHT_EYE
                )?.position

            val noseBase =
                face.getLandmark(
                    FaceLandmark.NOSE_BASE
                )?.position

            DetectedFace(
                timestampMs = timestampMs,

                boundingBox =
                    face.boundingBox,

                frame =
                    bitmap,

                headEulerAngleX =
                    face.headEulerAngleX,

                headEulerAngleY =
                    face.headEulerAngleY,

                headEulerAngleZ =
                    face.headEulerAngleZ,

                leftEyeOpenProbability =
                    face.leftEyeOpenProbability,

                rightEyeOpenProbability =
                    face.rightEyeOpenProbability,

                smilingProbability =
                    face.smilingProbability,

                leftEyePosition =
                    leftEye,

                rightEyePosition =
                    rightEye,

                noseBasePosition =
                    noseBase
            )
        }
    }

    fun close() {
        detector.close()
    }
}