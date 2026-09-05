package com.example.iykyk.processing

import android.util.Log
import com.example.iykyk.model.FaceEmbedding
import com.example.iykyk.model.Person
import kotlin.math.sqrt

class FaceClusterer {

    companion object {

        /*
         * Base similarity required for two appearances
         * to be considered the same person.
         */
        private const val SIMILARITY_THRESHOLD = 0.55f

        /*
         * Minimum similarity that the new appearance must
         * have with at least one reliable member of a cluster.
         *
         * This prevents a weak "bridge" appearance from joining
         * two different people merely because the average is high.
         */
        private const val STRONG_SIMILARITY = 0.72f

        /*
         * When calculating cluster similarity, at least one
         * reasonably strong relationship should exist.
         */
        private const val MIN_BEST_SIMILARITY = 0.68f
    }

    fun cluster(
        faceEmbeddings: List<FaceEmbedding>,
        appearanceGroups: List<List<FaceEmbedding>>
    ): List<Person> {

        if (faceEmbeddings.isEmpty()) {
            return emptyList()
        }

        /*
         * Every appearance starts as its own cluster.
         */
        val clusters =
            mutableListOf<MutableList<Int>>()

        for (i in faceEmbeddings.indices) {
            clusters.add(
                mutableListOf(i)
            )
        }

        /*
         * Pre-compute cosine similarity.
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

        /*
         * Keep the matrix in Logcat.
         * This is extremely useful for debugging the assignment.
         */
        Log.d(
            "IYKYK_SIM",
            "========== FULL SIMILARITY MATRIX =========="
        )

        for (i in faceEmbeddings.indices) {

            val row = StringBuilder()

            for (j in faceEmbeddings.indices) {

                row.append(
                    String.format(
                        "%.2f ",
                        similarity[i][j]
                    )
                )
            }

            Log.d(
                "IYKYK_SIM",
                "A${i + 1}: $row"
            )
        }

        /*
         * Agglomerative clustering.
         *
         * Instead of using only the average similarity,
         * we calculate a more conservative score.
         */
        while (true) {

            var bestA = -1
            var bestB = -1
            var bestScore = -1f

            for (i in 0 until clusters.size) {

                for (j in i + 1 until clusters.size) {

                    val score =
                        clusterMergeScore(
                            clusters[i],
                            clusters[j],
                            similarity,
                            appearanceGroups
                        )

                    if (score > bestScore) {

                        bestScore = score
                        bestA = i
                        bestB = j
                    }
                }
            }

            /*
             * No valid merge remaining.
             */
            if (
                bestA == -1 ||
                bestB == -1 ||
                bestScore < SIMILARITY_THRESHOLD
            ) {
                break
            }

            Log.d(
                "IYKYK_CLUSTER",
                "Merging clusters " +
                        "${bestA + 1} and ${bestB + 1} " +
                        "score=$bestScore"
            )

            clusters[bestA].addAll(
                clusters[bestB]
            )

            clusters.removeAt(bestB)
        }

        /*
         * Sort people according to when they first appear.
         */
        val sortedClusters =
            clusters.sortedBy { cluster ->

                cluster.minOf { faceIndex ->

                    faceEmbeddings[
                        faceIndex
                    ].face.timestampMs
                }
            }

        /*
         * Convert clusters to Person objects.
         */
        return sortedClusters.mapIndexed { index, cluster ->

            val person =
                Person(
                    id = index + 1
                )

            Log.d(
                "IYKYK_CLUSTER",
                "Cluster ${index + 1} detailed members:"
            )

            for (faceIndex in cluster) {

                val item =
                    faceEmbeddings[faceIndex]

                Log.d(
                    "IYKYK_CLUSTER",
                    "  A${faceIndex + 1}: " +
                            "${item.face.timestampMs} ms"
                )

                person.faces.add(
                    item.face
                )

                person.embeddings.add(
                    item.embedding
                )
            }

            Log.d(
                "IYKYK_CLUSTER",
                "Cluster ${index + 1}: " +
                        "${cluster.size} appearances -> " +
                        cluster.joinToString(
                            prefix = "[",
                            postfix = "]"
                        ) {
                            "A${it + 1}"
                        }
            )

            person
        }
    }

    /**
     * Calculates how strongly two clusters should be merged.
     *
     * We intentionally do NOT use plain average-linkage.
     *
     * A good merge should:
     *
     * 1. Have a good average similarity.
     * 2. Have at least one strong identity relationship.
     * 3. Not depend on overlapping appearances.
     */
    private fun clusterMergeScore(
        clusterA: List<Int>,
        clusterB: List<Int>,
        similarity: Array<FloatArray>,
        appearanceGroups: List<List<FaceEmbedding>>
    ): Float {

        var total =
            0f

        var count =
            0

        var bestSimilarity =
            -1f

        var strongCount =
            0

        for (a in clusterA) {

            for (b in clusterB) {

                /*
                 * Do not use simultaneous appearances as
                 * positive identity evidence.
                 */
                if (
                    appearancesOverlap(
                        appearanceGroups[a],
                        appearanceGroups[b]
                    )
                ) {
                    continue
                }

                val sim =
                    similarity[a][b]

                total += sim
                count++

                if (sim > bestSimilarity) {
                    bestSimilarity = sim
                }

                if (sim >= STRONG_SIMILARITY) {
                    strongCount++
                }
            }
        }

        /*
         * If every pair overlaps, we have no evidence
         * with which to merge the two groups.
         */
        if (count == 0) {
            return -1f
        }

        val average =
            total / count.toFloat()

        /*
         * There must be at least one strong identity
         * relationship.
         */
        if (
            bestSimilarity <
            MIN_BEST_SIMILARITY
        ) {
            return -1f
        }

        /*
         * A cluster should not be merged solely because
         * of one lucky high similarity.
         *
         * If there are multiple comparisons, require either:
         *
         * - a strong pair, OR
         * - a reasonably good average.
         *
         * This is intentionally soft rather than a hard
         * overlap constraint.
         */
        if (
            count >= 3 &&
            strongCount == 0 &&
            average < SIMILARITY_THRESHOLD
        ) {
            return -1f
        }

        /*
         * Combine average similarity and strongest identity
         * evidence.
         *
         * Average remains the main signal, while best similarity
         * prevents genuinely matching identities from being lost
         * because of pose/lighting variation.
         */
        return (
                average * 0.75f +
                        bestSimilarity * 0.25f
                )
    }

    /**
     * Returns true when two appearance intervals overlap.
     */
    private fun appearancesOverlap(
        appearanceA: List<FaceEmbedding>,
        appearanceB: List<FaceEmbedding>
    ): Boolean {

        if (
            appearanceA.isEmpty() ||
            appearanceB.isEmpty()
        ) {
            return false
        }

        val startA =
            appearanceA.first().face.timestampMs

        val endA =
            appearanceA.last().face.timestampMs

        val startB =
            appearanceB.first().face.timestampMs

        val endB =
            appearanceB.last().face.timestampMs

        return (
                startA <= endB &&
                        startB <= endA
                )
    }

    /**
     * Cosine similarity between two face embeddings.
     */
    private fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray
    ): Float {

        var dot =
            0f

        var magnitudeA =
            0f

        var magnitudeB =
            0f

        val size =
            minOf(
                a.size,
                b.size
            )

        for (i in 0 until size) {

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

        if (
            denominator <= 0f
        ) {
            return 0f
        }

        return dot / denominator
    }
}