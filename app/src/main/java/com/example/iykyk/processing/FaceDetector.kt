package com.example.iykyk.processing

import android.graphics.Bitmap
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
                FaceDetectorOptions.PERFORMANCE_MODE_FAST
            )
            .setLandmarkMode(
                FaceDetectorOptions.LANDMARK_MODE_ALL
            )
            .setClassificationMode(
                FaceDetectorOptions.CLASSIFICATION_MODE_ALL
            )
            .setMinFaceSize(0.08f)
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

        return faces.map { face ->

            val leftEye =
                face.getLandmark(
                    FaceLandmark.LEFT_EYE
                )?.position

            val rightEye =
                face.getLandmark(
                    FaceLandmark.RIGHT_EYE
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
                    rightEye
            )
        }
    }

    fun close() {
        detector.close()
    }
}