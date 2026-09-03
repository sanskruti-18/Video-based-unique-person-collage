package com.example.iykyk.model

data class Person (
    val id: Int,
    val faces: MutableList<DetectedFace> = mutableListOf(),
    val embeddings: MutableList<FloatArray> = mutableListOf(),
    val appearances: MutableList<Appearance> = mutableListOf()
) {
    val appearanceCount: Int
        get() = appearances.size
}