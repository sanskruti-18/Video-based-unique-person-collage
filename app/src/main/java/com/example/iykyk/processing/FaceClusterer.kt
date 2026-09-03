package com.example.iykyk.processing

import android.util.Log
import com.example.iykyk.model.FaceEmbedding
import com.example.iykyk.model.Person
import kotlin.math.sqrt

class FaceClusterer {

    companion object {

        /*
         * Average-linkage clustering threshold.
         *
         * Two groups are merged when the average similarity
         * between their embeddings is high enough.
         */
        private const val SIMILARITY_THRESHOLD = 0.55f
    }

    fun cluster(
        faceEmbeddings: List<FaceEmbedding>
    ): List<Person> {

        if (faceEmbeddings.isEmpty()) {
            return emptyList()
        }

        /*
         * Start with every appearance as its own cluster.
         */
        val clusters =
            mutableListOf<MutableList<Int>>()

        for (i in faceEmbeddings.indices) {
            clusters.add(
                mutableListOf(i)
            )
        }

        /*
         * Pre-compute pairwise cosine similarities.
         */
        val similarity =
            Array(faceEmbeddings.size) {
                FloatArray(faceEmbeddings.size)
            }

        for (i in faceEmbeddings.indices) {

            similarity[i][i] = 1f

            for (j in i + 1 until faceEmbeddings.size) {

                val value =
                    cosineSimilarity(
                        faceEmbeddings[i].embedding,
                        faceEmbeddings[j].embedding
                    )

                similarity[i][j] = value
                similarity[j][i] = value
            }
        }
        Log.d("IYKYK_SIM", "----- Appearance similarities -----")

        for (j in 1 until faceEmbeddings.size) {

            Log.d(
                "IYKYK_SIM",
                "Appearance 1 vs ${j + 1}: ${similarity[0][j]}"
            )
        }

        /*
         * Repeatedly find the two clusters whose
         * average pairwise similarity is highest.
         */
        while (true) {

            var bestA = -1
            var bestB = -1
            var bestSimilarity = -1f

            for (i in 0 until clusters.size) {

                for (j in i + 1 until clusters.size) {

                    val averageSimilarity =
                        averageClusterSimilarity(
                            clusters[i],
                            clusters[j],
                            similarity
                        )

                    if (
                        averageSimilarity >
                        bestSimilarity
                    ) {
                        bestSimilarity =
                            averageSimilarity

                        bestA = i
                        bestB = j
                    }
                }
            }

            /*
             * Stop when no remaining pair is similar
             * enough to represent the same person.
             */
            if (
                bestA == -1 ||
                bestB == -1 ||
                bestSimilarity <
                SIMILARITY_THRESHOLD
            ) {
                break
            }

            Log.d(
                "IYKYK_CLUSTER",
                "Merging clusters " +
                        "${bestA + 1} and ${bestB + 1} " +
                        "similarity=$bestSimilarity"
            )

            /*
             * Merge B into A.
             */
            clusters[bestA].addAll(
                clusters[bestB]
            )

            clusters.removeAt(bestB)
        }

        /*
         * Convert clusters into Person objects.
         */
        return clusters.mapIndexed { index, cluster ->

            val person =
                Person(
                    id = index + 1
                )

            for (faceIndex in cluster) {

                val item =
                    faceEmbeddings[faceIndex]

                person.faces.add(
                    item.face
                )

                person.embeddings.add(
                    item.embedding
                )
            }

            Log.d(
                "IYKYK",
                "Cluster ${index + 1}: " +
                        "${cluster.size} appearances"
            )

            person
        }
    }

    private fun averageClusterSimilarity(
        clusterA: List<Int>,
        clusterB: List<Int>,
        similarity: Array<FloatArray>
    ): Float {

        var total = 0f
        var count = 0

        for (a in clusterA) {

            for (b in clusterB) {

                total +=
                    similarity[a][b]

                count++
            }
        }

        if (count == 0) {
            return 0f
        }

        return total /
                count.toFloat()
    }

    private fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray
    ): Float {

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

        val denominator =
            sqrt(magnitudeA) *
                    sqrt(magnitudeB)

        if (denominator == 0f) {
            return 0f
        }

        return dot / denominator
    }
}