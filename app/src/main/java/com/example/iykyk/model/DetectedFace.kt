package com.example.iykyk.model

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect

data class DetectedFace(
    val timestampMs: Long,
    val boundingBox: Rect,
    val frame: Bitmap,

    val headEulerAngleX: Float,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,

    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?,

    val leftEyePosition: PointF?,
    val rightEyePosition: PointF?,
    val noseBasePosition: PointF? = null
)