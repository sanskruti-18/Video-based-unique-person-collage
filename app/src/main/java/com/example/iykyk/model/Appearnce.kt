package com.example.iykyk.model

data class Appearance(
    val personId: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val representativeFace: DetectedFace? = null
)