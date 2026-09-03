package com.example.iykyk.model

data class FaceEmbedding(
    val face: DetectedFace,
    val embedding: FloatArray
)