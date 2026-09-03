package com.example.iykyk

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import com.example.iykyk.data.VideoFrameExtractor
import com.example.iykyk.model.DetectedFace
import com.example.iykyk.model.FaceEmbedding
import com.example.iykyk.model.Person
import com.example.iykyk.processing.FaceClusterer
import com.example.iykyk.processing.FaceDetector
import com.example.iykyk.processing.FaceEmbedder
import com.example.iykyk.ui.ProcessingScreen
import com.example.iykyk.ui.theme.IykykTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.iykyk.model.Appearance
import com.example.iykyk.processing.AppearanceTracker
import com.example.iykyk.processing.RepresentativeSelector
import com.example.iykyk.processing.CollageGenerator
class MainActivity : ComponentActivity() {

    private lateinit var frameExtractor: VideoFrameExtractor
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceEmbedder: FaceEmbedder
    private lateinit var representativeSelector: RepresentativeSelector
    private lateinit var collageGenerator: CollageGenerator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // -----------------------------------------
        // INITIALIZE PROCESSING COMPONENTS
        // -----------------------------------------

        frameExtractor = VideoFrameExtractor(this)
        faceDetector = FaceDetector()
        faceEmbedder = FaceEmbedder(this)
        representativeSelector = RepresentativeSelector()
        collageGenerator = CollageGenerator()

        setContent {

            IykykTheme {

                // -----------------------------------------
                // UI STATE
                // -----------------------------------------

                var isProcessing by remember {
                    mutableStateOf(false)
                }

                var progress by remember {
                    mutableFloatStateOf(0f)
                }

                var status by remember {
                    mutableStateOf("")
                }

                var resultPeople by remember {
                    mutableStateOf<List<Person>>(emptyList())
                }

                var resultCollage by remember {
                    mutableStateOf<android.graphics.Bitmap?>(null)
                }

                // -----------------------------------------
                // VIDEO PICKER
                // -----------------------------------------

                val videoPicker =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri ->

                        if (uri != null) {

                            // Reset UI
                            isProcessing = true
                            progress = 0f
                            status = "Starting..."
                            resultPeople = emptyList()

                            // -----------------------------------------
                            // START PROCESSING
                            // -----------------------------------------

                            lifecycleScope.launch {

                                try {

                                    /*
                                     * Video processing is CPU/ML intensive,
                                     * so run it away from the main UI thread.
                                     */
                                    val people =
                                        withContext(Dispatchers.Default) {

                                            processVideo(
                                                uri = uri,
                                                onProgress = { value, message ->

                                                    /*
                                                     * processVideo runs on a
                                                     * background thread, so UI
                                                     * state is updated on Main.
                                                     */
                                                    withContext(
                                                        Dispatchers.Main
                                                    ) {

                                                        progress = value
                                                        status = message
                                                    }
                                                }
                                            )
                                        }

                                    // -----------------------------------------
                                    // PROCESSING FINISHED
                                    // -----------------------------------------

                                    val collage =
                                        collageGenerator.createCollage(
                                            people
                                        )

                                    resultPeople = people
                                    resultCollage = collage

                                    progress = 1f

                                    status =
                                        "Processing complete"

                                    Log.d(
                                        "IYKYK",
                                        "Final people = ${people.size}"
                                    )

                                } catch (e: Exception) {

                                    Log.e(
                                        "IYKYK",
                                        "Processing failed",
                                        e
                                    )

                                    status =
                                        "Processing failed: ${e.message}"

                                } finally {

                                    isProcessing = false
                                }
                            }
                        }
                    }

                // -----------------------------------------
                // SCREEN NAVIGATION
                // -----------------------------------------

                when {

                    isProcessing -> {

                        ProcessingScreen(
                            progress = progress,
                            status = status
                        )
                    }

                    resultPeople.isNotEmpty() -> {

                        ResultPreview(
                            people = resultPeople,
                            collage = resultCollage,
                            onSelectAnother = {

                                resultPeople =
                                    emptyList()

                                progress = 0f
                                status = ""

                                resultCollage =
                                    null
                            }
                        )
                    }

                    else -> {

                        HomeScreen(
                            onSelectVideo = {

                                videoPicker.launch(
                                    "video/*"
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // ============================================================
    // VIDEO PROCESSING PIPELINE
    // ============================================================

    private suspend fun processVideo(
        uri: Uri,
        onProgress: suspend (Float, String) -> Unit
    ): List<Person> {

        // ========================================================
        // STEP 1 — EXTRACT FRAMES
        // ========================================================

        onProgress(
            0f,
            "Extracting frames..."
        )

        val frames =
            frameExtractor.extractFrames(uri)

        Log.d(
            "IYKYK",
            "Frames extracted = ${frames.size}"
        )

        if (frames.isEmpty()) {

            Log.d(
                "IYKYK",
                "No frames extracted"
            )

            return emptyList()
        }

        // ========================================================
        // STEP 2 — DETECT FACES
        // ========================================================

        val detectedFaces =
            mutableListOf<DetectedFace>()

        frames.forEachIndexed { index, pair ->

            val timestampMs =
                pair.first

            val bitmap =
                pair.second

            val faces =
                faceDetector.detectFaces(
                    timestampMs,
                    bitmap
                )

            detectedFaces.addAll(
                faces
            )

            /*
             * Face detection occupies approximately
             * 40% of the overall progress.
             */
            val detectionProgress =
                0.4f *
                        (index + 1).toFloat() /
                        frames.size

            onProgress(
                detectionProgress,
                "Detecting faces..."
            )
        }

        Log.d(
            "IYKYK",
            "Faces detected = ${detectedFaces.size}"
        )
        val faceCountAtTimestamp =
            detectedFaces
                .groupingBy {
                    it.timestampMs
                }
                .eachCount()

        if (detectedFaces.isEmpty()) {

            Log.d(
                "IYKYK",
                "No faces detected"
            )

            return emptyList()
        }

        // ========================================================
        // STEP 3 — GENERATE FACE EMBEDDINGS
        // ========================================================

        val faceEmbeddings =
            mutableListOf<FaceEmbedding>()

        detectedFaces.forEachIndexed { index, face ->

            val embedding =
                faceEmbedder.getEmbedding(
                    face
                )

            /*
             * Store face + its 192-dimensional
             * MobileFaceNet embedding.
             */
            faceEmbeddings.add(
                FaceEmbedding(
                    face = face,
                    embedding = embedding
                )
            )

            /*
             * Embedding stage occupies approximately
             * 30% of total progress.
             */
            val embeddingProgress =
                0.4f +
                        0.3f *
                        (index + 1).toFloat() /
                        detectedFaces.size

            onProgress(
                embeddingProgress,
                "Generating embeddings..."
            )
        }

        Log.d(
            "IYKYK",
            "Embeddings generated = ${faceEmbeddings.size}"
        )

        // ========================================================
// STEP 4 — FIND CONTINUOUS APPEARANCES
// ========================================================

        onProgress(
            0.75f,
            "Finding appearances..."
        )

        val appearanceTracker =
            AppearanceTracker()

        val appearanceGroups =
            appearanceTracker.createAppearances(
                faceEmbeddings
            )

        Log.d(
            "IYKYK",
            "Appearances found = ${appearanceGroups.size}"
        )

        appearanceGroups.forEachIndexed { index, appearance ->

            val start =
                appearance.first().face.timestampMs

            val end =
                appearance.last().face.timestampMs

            Log.d(
                "IYKYK",
                "Appearance ${index + 1}: " +
                        "${appearance.size} frames, " +
                        "$start ms - $end ms"
            )
        }

        if (appearanceGroups.isEmpty()) {
            return emptyList()
        }

// ========================================================
// STEP 5 — CREATE ONE EMBEDDING PER APPEARANCE
// ========================================================

        onProgress(
            0.85f,
            "Identifying people..."
        )

        val appearanceEmbeddings =
            appearanceGroups.map { group ->

                val embeddingSize =
                    group.first().embedding.size

                val average =
                    FloatArray(embeddingSize)

                for (item in group) {

                    for (i in 0 until embeddingSize) {
                        average[i] +=
                            item.embedding[i]
                    }
                }

                for (i in average.indices) {

                    average[i] /=
                        group.size.toFloat()
                }

                /*
                 * L2-normalize the averaged embedding.
                 */
                var magnitude = 0f

                for (value in average) {
                    magnitude +=
                        value * value
                }

                magnitude =
                    kotlin.math.sqrt(magnitude)

                if (magnitude > 0f) {

                    for (i in average.indices) {

                        average[i] /=
                            magnitude
                    }
                }

                /*
                 * Temporary representative.
                 *
                 * We will replace this later with
                 * RepresentativeSelector.
                 */
                val representative =
                    group[group.size / 2].face

                FaceEmbedding(
                    face = representative,
                    embedding = average
                )
            }

        Log.d(
            "IYKYK",
            "Appearance embeddings = " +
                    appearanceEmbeddings.size
        )

// ========================================================
// STEP 6 — CLUSTER APPEARANCES INTO PEOPLE
// ========================================================

        val clusterer =
            FaceClusterer()

        val people =
            clusterer.cluster(
                appearanceEmbeddings
            )

        Log.d(
            "IYKYK",
            "People found = ${people.size}"
        )
        // ========================================================
// DEBUG — MAP EVERY APPEARANCE TO ITS PERSON
// ========================================================

        for (person in people) {

            for (representativeFace in person.faces) {

                val appearanceIndex =
                    appearanceEmbeddings.indexOfFirst {
                        it.face === representativeFace
                    }

                if (appearanceIndex >= 0) {

                    val group =
                        appearanceGroups[appearanceIndex]

                    val start =
                        group.first().face.timestampMs

                    val end =
                        group.last().face.timestampMs

                    Log.d(
                        "IYKYK_MAPPING",
                        "Appearance ${appearanceIndex + 1}: " +
                                "$start-$end ms -> " +
                                "Person ${person.id}"
                    )
                }
            }
        }


// ========================================================
// STEP 7 — STORE REAL APPEARANCES IN EACH PERSON
// ========================================================

        for (person in people) {

            /*
             * The FaceClusterer stores the representative
             * face of each appearance in person.faces.
             *
             * Match each representative face back to its
             * original appearance group.
             */
            for (representativeFace in person.faces) {

                val group =
                    appearanceGroups.firstOrNull { appearance ->

                        appearance.any { item ->

                            item.face ===
                                    representativeFace
                        }
                    }

                if (group != null) {

                    addAppearance(
                        person = person,
                        faces =
                            group.map {
                                it.face
                            },
                        faceCountAtTimestamp =
                            faceCountAtTimestamp
                    )
                }
            }
        }

        for (person in people) {

            Log.d(
                "IYKYK",
                "Person ${person.id}: " +
                        "${person.appearanceCount} appearances"
            )
        }
        onProgress(
            1f,
            "Found ${people.size} people"
        )

        return people
    }
    private fun addAppearance(
        person: Person,
        faces: List<DetectedFace>,
        faceCountAtTimestamp: Map<Long, Int>
    ) {

        if (faces.isEmpty()) {
            return
        }

        val startTimeMs =
            faces.first().timestampMs

        val endTimeMs =
            faces.last().timestampMs

        /*
         * Select the best frame from this appearance
         * using pose, eyes, sharpness, expression,
         * face size and completeness.
         */
        val representativeFace =
            representativeSelector.selectBestFace(
                faces = faces,
                faceCountAtTimestamp =
                    faceCountAtTimestamp
            )
        Log.d(
            "IYKYK_REP",
            "Person ${person.id}: " +
                    "selected=${representativeFace?.timestampMs}ms, " +
                    "facesInFrame=${
                        representativeFace?.let {
                            faceCountAtTimestamp[it.timestampMs] ?: 1
                        }
                    }"
        )

        if (representativeFace == null) {
            return
        }

        person.appearances.add(
            Appearance(
                personId = person.id,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                representativeFace = representativeFace
            )
        )

        Log.d(
            "IYKYK",
            "Person ${person.id} appearance: " +
                    "$startTimeMs ms - $endTimeMs ms " +
                    "representative=${representativeFace.timestampMs} ms"
        )
    }

    // ============================================================
    // HOME SCREEN
    // ============================================================

    @Composable
    fun HomeScreen(
        onSelectVideo: () -> Unit
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "IYKYK"
            )

            Button(
                onClick = onSelectVideo
            ) {

                Text(
                    text = "Select Video"
                )
            }
        }
    }

    // ============================================================
    // TEMPORARY RESULT SCREEN
    // ============================================================

    @Composable
    fun ResultPreview(
        people: List<Person>,
        collage: android.graphics.Bitmap?,
        onSelectAnother: () -> Unit
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "Processing Complete"
            )

            Text(
                text = "Found ${people.size} people"
            )

            if (collage != null) {

                androidx.compose.foundation.Image(
                    bitmap = collage.asImageBitmap(),
                    contentDescription = "Generated collage",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                )
            }

            people.forEach { person ->

                Text(
                    text =
                        "Person ${person.id}: " +
                                "${person.appearanceCount} appearances"
                )
            }

            Button(
                onClick = onSelectAnother
            ) {

                Text(
                    text = "Select Another Video"
                )
            }
        }
    }

    // ============================================================
    // CLEAN UP
    // ============================================================

    override fun onDestroy() {

        faceDetector.close()
        faceEmbedder.close()

        super.onDestroy()
    }
}