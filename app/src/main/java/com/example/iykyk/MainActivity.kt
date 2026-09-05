package com.example.iykyk

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope

import com.example.iykyk.data.VideoFrameExtractor
import com.example.iykyk.model.Appearance
import com.example.iykyk.model.DetectedFace
import com.example.iykyk.model.FaceEmbedding
import com.example.iykyk.model.Person
import com.example.iykyk.processing.AppearanceTracker
import com.example.iykyk.processing.CollageGenerator
import com.example.iykyk.processing.FaceClusterer
import com.example.iykyk.processing.FaceDetector
import com.example.iykyk.processing.FaceEmbedder
import com.example.iykyk.processing.RepresentativeSelector
import com.example.iykyk.ui.screens.HomeScreen
import com.example.iykyk.ui.screens.ProcessingScreen
import com.example.iykyk.ui.screens.ResultScreen
import com.example.iykyk.ui.theme.IykykTheme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File


class MainActivity : ComponentActivity() {

    private lateinit var frameExtractor: VideoFrameExtractor
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceEmbedder: FaceEmbedder
    private lateinit var representativeSelector: RepresentativeSelector
    private lateinit var collageGenerator: CollageGenerator


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // =========================================================
        // INITIALIZE PROCESSING COMPONENTS
        // =========================================================

        frameExtractor =
            VideoFrameExtractor(this)

        faceDetector =
            FaceDetector()

        faceEmbedder =
            FaceEmbedder(this)

        representativeSelector =
            RepresentativeSelector()

        collageGenerator =
            CollageGenerator()


        // =========================================================
        // COMPOSE UI
        // =========================================================

        setContent {

            IykykTheme {

                // -------------------------------------------------
                // UI STATE
                // -------------------------------------------------

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
                    mutableStateOf<List<Person>>(
                        emptyList()
                    )
                }

                var resultCollage by remember {
                    mutableStateOf<Bitmap?>(null)
                }


                // =================================================
                // VIDEO PICKER
                // =================================================

                val videoPicker =
                    rememberLauncherForActivityResult(
                        contract =
                            ActivityResultContracts.GetContent()
                    ) { uri ->

                        if (uri == null) {
                            return@rememberLauncherForActivityResult
                        }

                        // Reset previous result
                        resultPeople =
                            emptyList()

                        resultCollage =
                            null

                        progress =
                            0f

                        status =
                            "Starting..."

                        isProcessing =
                            true


                        // =================================================
                        // PROCESS VIDEO IN BACKGROUND
                        // =================================================

                        lifecycleScope.launch {

                            try {

                                val result =
                                    withContext(
                                        Dispatchers.Default
                                    ) {

                                        processVideo(
                                            uri = uri,
                                            onProgress = {
                                                    value,
                                                    message ->

                                                withContext(
                                                    Dispatchers.Main
                                                ) {

                                                    progress =
                                                        value

                                                    status =
                                                        message
                                                }
                                            }
                                        )
                                    }


                                val people =
                                    result.first

                                val faceCounts =
                                    result.second


                                // =================================================
                                // GENERATE COLLAGE
                                // =================================================

                                status =
                                    "Creating collage..."


                                val collage =
                                    withContext(
                                        Dispatchers.Default
                                    ) {

                                        collageGenerator.createCollage(
                                            people = people,
                                            faceCountAtTimestamp =
                                                faceCounts
                                        )
                                    }


                                // =================================================
                                // UPDATE UI
                                // =================================================

                                resultPeople =
                                    people

                                resultCollage =
                                    collage

                                progress =
                                    1f

                                status =
                                    "Processing complete"


                                Log.d(
                                    "IYKYK",
                                    "Final people = ${people.size}"
                                )

                                Log.d(
                                    "IYKYK",
                                    "Total appearances = ${
                                        people.sumOf {
                                            it.appearanceCount
                                        }
                                    }"
                                )

                            } catch (e: Exception) {

                                Log.e(
                                    "IYKYK",
                                    "Processing failed",
                                    e
                                )

                                status =
                                    "Processing failed: ${
                                        e.message
                                    }"

                            } finally {

                                isProcessing =
                                    false
                            }
                        }
                    }


                // =================================================
                // SCREEN NAVIGATION
                // =================================================

                when {

                    // -------------------------------------------------
                    // PROCESSING SCREEN
                    // -------------------------------------------------

                    isProcessing -> {

                        ProcessingScreen(
                            progress =
                                progress,
                            status =
                                status
                        )
                    }


                    // -------------------------------------------------
                    // RESULT SCREEN
                    // -------------------------------------------------

                    resultPeople.isNotEmpty() -> {

                        ResultScreen(

                            people =
                                resultPeople,

                            collage =
                                resultCollage,


                            // -----------------------------------------
                            // SAVE
                            // -----------------------------------------

                            onSaveToGallery = {

                                resultCollage?.let {

                                    saveCollageToGallery(
                                        it
                                    )
                                }
                            },


                            // -----------------------------------------
                            // SHARE
                            // -----------------------------------------

                            onShareCollage = {

                                resultCollage?.let {

                                    shareCollage(
                                        it
                                    )
                                }
                            },


                            // -----------------------------------------
                            // SELECT ANOTHER VIDEO
                            // -----------------------------------------

                            onSelectAnother = {

                                resultPeople =
                                    emptyList()

                                resultCollage =
                                    null

                                progress =
                                    0f

                                status =
                                    ""
                            }
                        )
                    }


                    // -------------------------------------------------
                    // HOME SCREEN
                    // -------------------------------------------------

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


    // =============================================================
    // VIDEO PROCESSING PIPELINE
    // =============================================================

    private suspend fun processVideo(
        uri: Uri,
        onProgress: suspend (
            Float,
            String
        ) -> Unit
    ): Pair<List<Person>, Map<Long, Int>> {


        // =========================================================
        // STEP 1 — FRAME EXTRACTION
        // =========================================================

        onProgress(
            0f,
            "Extracting frames..."
        )


        val frames =
            frameExtractor.extractFrames(
                uri
            )


        Log.d(
            "IYKYK",
            "Frames extracted = ${frames.size}"
        )


        if (frames.isEmpty()) {

            return Pair(
                emptyList(),
                emptyMap()
            )
        }


        // =========================================================
        // STEP 2 — FACE DETECTION
        // =========================================================

        val detectedFaces =
            mutableListOf<DetectedFace>()


        frames.forEachIndexed {
                index,
                pair ->

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


            val detectionProgress =
                0.4f *
                        (
                                index + 1
                                ).toFloat() /
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

            return Pair(
                emptyList(),
                emptyMap()
            )
        }


        // =========================================================
        // STEP 3 — FACE EMBEDDINGS
        // =========================================================

        val faceEmbeddings =
            mutableListOf<FaceEmbedding>()


        detectedFaces.forEachIndexed {
                index,
                face ->

            val embedding =
                faceEmbedder.getEmbedding(
                    face
                )


            faceEmbeddings.add(
                FaceEmbedding(
                    face =
                        face,
                    embedding =
                        embedding
                )
            )


            val embeddingProgress =
                0.4f +
                        0.3f *
                        (
                                index + 1
                                ).toFloat() /
                        detectedFaces.size


            onProgress(
                embeddingProgress,
                "Generating embeddings..."
            )
        }


        Log.d(
            "IYKYK",
            "Embeddings generated = ${
                faceEmbeddings.size
            }"
        )


        // =========================================================
        // STEP 4 — CONTINUOUS APPEARANCES
        // =========================================================

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
            "Appearances found = ${
                appearanceGroups.size
            }"
        )


        appearanceGroups.forEachIndexed {
                index,
                appearance ->

            val start =
                appearance
                    .first()
                    .face
                    .timestampMs

            val end =
                appearance
                    .last()
                    .face
                    .timestampMs


            Log.d(
                "IYKYK",
                "Appearance ${index + 1}: " +
                        "${appearance.size} frames, " +
                        "$start ms - $end ms"
            )
        }


        if (appearanceGroups.isEmpty()) {

            return Pair(
                emptyList(),
                emptyMap()
            )
        }


        // =========================================================
        // STEP 5 — ONE EMBEDDING PER APPEARANCE
        // =========================================================

        onProgress(
            0.85f,
            "Identifying people..."
        )


        val appearanceEmbeddings =
            appearanceGroups.map { group ->

                val embeddingSize =
                    group
                        .first()
                        .embedding
                        .size


                val average =
                    FloatArray(
                        embeddingSize
                    )


                // -----------------------------------------------
                // Average embeddings
                // -----------------------------------------------

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


                // -----------------------------------------------
                // L2 normalization
                // -----------------------------------------------

                var magnitude =
                    0f


                for (value in average) {

                    magnitude +=
                        value * value
                }


                magnitude =
                    kotlin.math.sqrt(
                        magnitude
                    )


                if (magnitude > 0f) {

                    for (i in average.indices) {

                        average[i] /=
                            magnitude
                    }
                }


                // -----------------------------------------------
                // Representative frame
                // -----------------------------------------------

                val representative =
                    representativeSelector
                        .selectBestFace(
                            faces =
                                group.map {
                                    it.face
                                },

                            faceCountAtTimestamp =
                                faceCountAtTimestamp
                        )
                        ?: group[
                            group.size / 2
                        ].face


                FaceEmbedding(
                    face =
                        representative,

                    embedding =
                        average
                )
            }


        Log.d(
            "IYKYK",
            "Appearance embeddings = ${
                appearanceEmbeddings.size
            }"
        )


        // =========================================================
        // STEP 6 — CLUSTER PEOPLE
        // =========================================================

        val clusterer =
            FaceClusterer()


        val people =
            clusterer.cluster(
                appearanceEmbeddings,
                appearanceGroups
            )


        Log.d(
            "IYKYK",
            "People found = ${people.size}"
        )


        // =========================================================
        // DEBUG — APPEARANCE MAPPING
        // =========================================================

        for (person in people) {

            for (
            representativeFace
            in person.faces
            ) {

                val appearanceIndex =
                    appearanceEmbeddings
                        .indexOfFirst {

                            it.face ===
                                    representativeFace
                        }


                if (appearanceIndex >= 0) {

                    val group =
                        appearanceGroups[
                            appearanceIndex
                        ]


                    val start =
                        group
                            .first()
                            .face
                            .timestampMs

                    val end =
                        group
                            .last()
                            .face
                            .timestampMs


                    Log.d(
                        "IYKYK_MAPPING",
                        "Appearance ${
                            appearanceIndex + 1
                        }: " +
                                "$start-$end ms -> " +
                                "Person ${person.id}"
                    )
                }
            }
        }


        // =========================================================
        // STEP 7 — STORE REAL APPEARANCES
        // =========================================================

        for (person in people) {

            for (
            representativeFace
            in person.faces
            ) {

                val group =
                    appearanceGroups
                        .firstOrNull { appearance ->

                            appearance.any { item ->

                                item.face ===
                                        representativeFace
                            }
                        }


                if (group != null) {

                    addAppearance(
                        person =
                            person,

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


        // =========================================================
        // FINAL LOGS
        // =========================================================

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


        return Pair(
            people,
            faceCountAtTimestamp
        )
    }


    // =============================================================
    // ADD APPEARANCE
    // =============================================================

    private fun addAppearance(
        person: Person,
        faces: List<DetectedFace>,
        faceCountAtTimestamp: Map<Long, Int>
    ) {

        if (faces.isEmpty()) {
            return
        }


        val startTimeMs =
            faces
                .first()
                .timestampMs


        val endTimeMs =
            faces
                .last()
                .timestampMs


        val representativeFace =
            representativeSelector
                .selectBestFace(
                    faces =
                        faces,

                    faceCountAtTimestamp =
                        faceCountAtTimestamp
                )


        Log.d(
            "IYKYK_REP",
            "Person ${person.id}: " +
                    "selected=${
                        representativeFace
                            ?.timestampMs
                    }ms, " +
                    "facesInFrame=${
                        representativeFace?.let {

                            faceCountAtTimestamp[
                                it.timestampMs
                            ] ?: 1
                        }
                    }"
        )


        if (representativeFace == null) {
            return
        }


        person.appearances.add(

            Appearance(

                personId =
                    person.id,

                startTimeMs =
                    startTimeMs,

                endTimeMs =
                    endTimeMs,

                representativeFace =
                    representativeFace
            )
        )


        Log.d(
            "IYKYK",
            "Person ${person.id} appearance: " +
                    "$startTimeMs ms - " +
                    "$endTimeMs ms " +
                    "representative=" +
                    "${representativeFace.timestampMs} ms"
        )
    }


    // =============================================================
    // SAVE COLLAGE
    // =============================================================

    private fun saveCollageToGallery(
        bitmap: Bitmap
    ) {

        try {

            val filename =
                "IYKYK_Collage_${
                    System.currentTimeMillis()
                }.jpg"


            val values =
                ContentValues().apply {

                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        filename
                    )

                    put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/jpeg"
                    )

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                    ) {

                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES +
                                    "/IYKYK"
                        )

                        put(
                            MediaStore.Images.Media.IS_PENDING,
                            1
                        )
                    }
                }


            val uri =
                contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )


            if (uri == null) {

                Toast.makeText(
                    this,
                    "Unable to save collage",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }


            contentResolver
                .openOutputStream(uri)
                ?.use { outputStream ->

                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        95,
                        outputStream
                    )
                }


            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {

                val updateValues =
                    ContentValues().apply {

                        put(
                            MediaStore.Images.Media.IS_PENDING,
                            0
                        )
                    }


                contentResolver.update(
                    uri,
                    updateValues,
                    null,
                    null
                )
            }


            Toast.makeText(
                this,
                "Collage saved to Gallery",
                Toast.LENGTH_SHORT
            ).show()


        } catch (e: Exception) {

            Log.e(
                "IYKYK",
                "Error saving collage",
                e
            )


            Toast.makeText(
                this,
                "Failed to save collage",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // =============================================================
    // SHARE COLLAGE
    // =============================================================

    private fun shareCollage(
        bitmap: Bitmap
    ) {

        try {

            val shareDirectory =
                File(
                    cacheDir,
                    "shared_collage"
                )


            if (!shareDirectory.exists()) {
                shareDirectory.mkdirs()
            }


            val file =
                File(
                    shareDirectory,
                    "IYKYK_collage.jpg"
                )


            file.outputStream().use { outputStream ->

                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    outputStream
                )
            }


            val uri =
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )


            val shareIntent =
                Intent(
                    Intent.ACTION_SEND
                ).apply {

                    type =
                        "image/jpeg"

                    putExtra(
                        Intent.EXTRA_STREAM,
                        uri
                    )

                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "IYKYK Collage"
                    )

                    putExtra(
                        Intent.EXTRA_TEXT,
                        "My IYKYK collage"
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }


            startActivity(
                Intent.createChooser(
                    shareIntent,
                    "Share IYKYK Collage"
                )
            )


        } catch (e: Exception) {

            Log.e(
                "IYKYK",
                "Error sharing collage",
                e
            )


            Toast.makeText(
                this,
                "Failed to share collage",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // =============================================================
    // CLEAN UP
    // =============================================================

    override fun onDestroy() {

        faceDetector.close()

        faceEmbedder.close()

        super.onDestroy()
    }
}