package com.example.iykyk.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.iykyk.model.DetectedFace
import com.example.iykyk.model.Person
import kotlin.math.sqrt

class CollageGenerator {

    companion object {

        // =========================================================
        // INSTAGRAM STORY
        // =========================================================

        private const val STORY_WIDTH = 1080
        private const val STORY_HEIGHT = 1920

        // =========================================================
        // COLLAGE SPACING
        // =========================================================

        private const val OUTER_PADDING = 48f
        private const val TILE_GAP = 18f
        private const val CORNER_RADIUS = 24f

        /*
         * Keep the generated collage visually clean.
         *
         * The ResultScreen already displays:
         * PEOPLE
         * APPEARANCES
         *
         * Therefore we intentionally do NOT draw another header
         * containing the same information inside the collage.
         */

        private val BACKGROUND =
            Color.rgb(
                10,
                15,
                28
            )
    }

    // =============================================================
    // CREATE COLLAGE
    // =============================================================

    fun createCollage(
        people: List<Person>,
        faceCountAtTimestamp: Map<Long, Int> = emptyMap()
    ): Bitmap {

        if (people.isEmpty()) {
            return createEmptyBitmap()
        }

        /*
         * ---------------------------------------------------------
         * Select exactly ONE representative image per person.
         * ---------------------------------------------------------
         *
         * Identity/tracking/clustering is NOT modified here.
         */
        val representatives =
            people.mapNotNull { person ->

                val representative =
                    selectPersonRepresentative(
                        person = person,
                        faceCountAtTimestamp =
                            faceCountAtTimestamp
                    )

                if (representative != null) {

                    representative

                } else {

                    null
                }
            }

        if (representatives.isEmpty()) {
            return createEmptyBitmap()
        }

        /*
         * ---------------------------------------------------------
         * Create Instagram Story canvas.
         * ---------------------------------------------------------
         */

        val collage =
            Bitmap.createBitmap(
                STORY_WIDTH,
                STORY_HEIGHT,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(collage)

        canvas.drawColor(
            BACKGROUND
        )

        /*
         * ---------------------------------------------------------
         * Adaptive tile layout.
         * ---------------------------------------------------------
         */

        val tileRects =
            createAdaptiveLayout(
                count = representatives.size
            )

        representatives.forEachIndexed { index, face ->

            if (index >= tileRects.size) {
                return@forEachIndexed
            }

            val tileRect =
                tileRects[index]

            val facesInFrame =
                faceCountAtTimestamp[
                    face.timestampMs
                ] ?: 1

            drawPersonTile(
                canvas = canvas,
                tileRect = tileRect,
                face = face,
                facesInFrame = facesInFrame
            )
        }

        return collage
    }

    // =============================================================
    // EMPTY
    // =============================================================

    private fun createEmptyBitmap(): Bitmap {

        return Bitmap.createBitmap(
            STORY_WIDTH,
            STORY_HEIGHT,
            Bitmap.Config.ARGB_8888
        ).apply {

            eraseColor(
                BACKGROUND
            )
        }
    }

    // =============================================================
    // ADAPTIVE LAYOUT
    // =============================================================

    private fun createAdaptiveLayout(
        count: Int
    ): List<RectF> {

        val left = OUTER_PADDING
        val right = STORY_WIDTH - OUTER_PADDING

        val top = 220f
        val bottom = STORY_HEIGHT - 120f

        val width = right - left
        val availableHeight = bottom - top

        val gap = 24f

        return when {

            // =====================================================
            // 1 PERSON
            // =====================================================

            count == 1 -> {

                val size = minOf(
                    width * 0.78f,
                    availableHeight * 0.45f
                )

                val x =
                    (STORY_WIDTH - size) / 2f

                val y =
                    top + 120f

                listOf(
                    RectF(
                        x,
                        y,
                        x + size,
                        y + size
                    )
                )
            }


            // =====================================================
            // 2 PEOPLE
            // =====================================================

            count == 2 -> {

                val size =
                    minOf(
                        (width - gap) / 2f,
                        500f
                    )

                val totalWidth =
                    size * 2f + gap

                val startX =
                    (STORY_WIDTH - totalWidth) / 2f

                val y =
                    top + 180f

                listOf(

                    RectF(
                        startX,
                        y,
                        startX + size,
                        y + size
                    ),

                    RectF(
                        startX + size + gap,
                        y,
                        startX + size * 2f + gap,
                        y + size
                    )
                )
            }


            // =====================================================
            // 3 PEOPLE
            // =====================================================

            count == 3 -> {

                val size =
                    minOf(
                        (width - gap * 2f) / 3f,
                        330f
                    )

                val totalWidth =
                    size * 3f + gap * 2f

                val startX =
                    (STORY_WIDTH - totalWidth) / 2f

                val y =
                    top + 200f

                listOf(

                    RectF(
                        startX,
                        y,
                        startX + size,
                        y + size
                    ),

                    RectF(
                        startX + size + gap,
                        y,
                        startX + size * 2f + gap,
                        y + size
                    ),

                    RectF(
                        startX + size * 2f + gap * 2f,
                        y,
                        startX + size * 3f + gap * 2f,
                        y + size
                    )
                )
            }


            // =====================================================
            // 4 PEOPLE
            // =====================================================

            count == 4 -> {

                createCenteredGrid(
                    count = 4,
                    columns = 2,
                    top = top + 150f,
                    width = width,
                    gap = gap,
                    maxTileSize = 420f
                )
            }


            // =====================================================
            // 5 PEOPLE
            // =====================================================

            count == 5 -> {

                val tileSize =
                    minOf(
                        (width - gap * 2f) / 3f,
                        320f
                    )

                val totalTopWidth =
                    tileSize * 3f +
                            gap * 2f

                val startX =
                    (STORY_WIDTH -
                            totalTopWidth) / 2f

                val topY =
                    top + 140f

                val bottomY =
                    topY +
                            tileSize +
                            gap


                listOf(

                    // -------------------------
                    // TOP 1
                    // -------------------------

                    RectF(
                        startX,
                        topY,
                        startX + tileSize,
                        topY + tileSize
                    ),

                    // -------------------------
                    // TOP 2
                    // -------------------------

                    RectF(
                        startX + tileSize + gap,
                        topY,
                        startX + tileSize * 2f + gap,
                        topY + tileSize
                    ),

                    // -------------------------
                    // TOP 3
                    // -------------------------

                    RectF(
                        startX + tileSize * 2f + gap * 2f,
                        topY,
                        startX + tileSize * 3f + gap * 2f,
                        topY + tileSize
                    ),

                    // -------------------------
                    // BOTTOM 4
                    // -------------------------

                    RectF(
                        (STORY_WIDTH -
                                tileSize * 2f -
                                gap) / 2f,
                        bottomY,
                        (STORY_WIDTH -
                                tileSize * 2f -
                                gap) / 2f +
                                tileSize,
                        bottomY + tileSize
                    ),

                    // -------------------------
                    // BOTTOM 5
                    // -------------------------

                    RectF(
                        (STORY_WIDTH -
                                tileSize * 2f -
                                gap) / 2f +
                                tileSize +
                                gap,
                        bottomY,
                        (STORY_WIDTH -
                                tileSize * 2f -
                                gap) / 2f +
                                tileSize * 2f +
                                gap,
                        bottomY + tileSize
                    )
                )
            }


            // =====================================================
            // 6 PEOPLE
            // =====================================================

            count == 6 -> {

                createCenteredGrid(
                    count = 6,
                    columns = 3,
                    top = top + 120f,
                    width = width,
                    gap = gap,
                    maxTileSize = 320f
                )
            }


            // =====================================================
            // 7+ PEOPLE
            // =====================================================

            else -> {

                val columns =
                    if (count <= 9) 3 else 4

                createCenteredGrid(
                    count = count,
                    columns = columns,
                    top = top + 80f,
                    width = width,
                    gap = gap,
                    maxTileSize =
                        if (columns == 3)
                            300f
                        else
                            235f
                )
            }
        }
    }
    private fun createCenteredGrid(
        count: Int,
        columns: Int,
        top: Float,
        width: Float,
        gap: Float,
        maxTileSize: Float
    ): List<RectF> {

        val tileSize =
            minOf(
                (
                        width -
                                gap * (columns - 1)
                        ) / columns,

                maxTileSize
            )

        val rows =
            (count + columns - 1) /
                    columns

        val result =
            mutableListOf<RectF>()

        var index = 0

        for (row in 0 until rows) {

            val itemsInRow =
                minOf(
                    columns,
                    count - index
                )

            val rowWidth =
                itemsInRow * tileSize +
                        (itemsInRow - 1) * gap

            val startX =
                (STORY_WIDTH - rowWidth) / 2f

            val y =
                top +
                        row *
                        (tileSize + gap)

            for (column in 0 until itemsInRow) {

                val x =
                    startX +
                            column *
                            (tileSize + gap)

                result.add(
                    RectF(
                        x,
                        y,
                        x + tileSize,
                        y + tileSize
                    )
                )

                index++
            }
        }

        return result
    }
    private fun createGenerousPortraitCrop(
        face: DetectedFace,
        facesInFrame: Int
    ): Bitmap {

        val bitmap =
            face.frame

        val box =
            face.boundingBox

        val faceWidth =
            box.width().toFloat()

        val faceHeight =
            box.height().toFloat()

        if (
            faceWidth <= 0f ||
            faceHeight <= 0f
        ) {
            return bitmap
        }


        // =========================================================
        // CENTER
        // =========================================================

        val centerX =
            box.exactCenterX()

        val centerY =
            box.exactCenterY()


        // =========================================================
        // SQUARE-ISH CROP
        //
        // Keep enough context around the face without letting the
        // crop become the entire frame.
        // =========================================================

        val cropSize =
            if (facesInFrame <= 1) {

                // Single-person frame:
                // generous but still controlled.
                faceWidth * 2.15f

            } else {

                // Shared frame:
                // tighter horizontally to avoid neighboring people.
                faceWidth * 1.45f
            }


        val maxCropWidth =
            if (facesInFrame <= 1) {

                bitmap.width * 0.82f

            } else {

                bitmap.width * 0.52f
            }


        val finalCropSize =
            minOf(
                cropSize,
                maxCropWidth,
                bitmap.height.toFloat()
            )


        // =========================================================
        // CENTER AROUND FACE
        // =========================================================

        var left =
            (
                    centerX -
                            finalCropSize / 2f
                    ).toInt()

        var right =
            (
                    centerX +
                            finalCropSize / 2f
                    ).toInt()

        var top =
            (
                    centerY -
                            finalCropSize * 0.43f
                    ).toInt()

        var bottom =
            (
                    top +
                            finalCropSize
                    ).toInt()


        // =========================================================
        // KEEP CROP INSIDE FRAME
        // =========================================================

        if (left < 0) {

            right -= left
            left = 0
        }

        if (right > bitmap.width) {

            left -=
                right - bitmap.width

            right =
                bitmap.width
        }


        if (top < 0) {

            bottom -= top
            top = 0
        }

        if (bottom > bitmap.height) {

            top -=
                bottom - bitmap.height

            bottom =
                bitmap.height
        }


        // =========================================================
        // FINAL SAFETY
        // =========================================================

        left =
            left.coerceIn(
                0,
                bitmap.width - 1
            )

        top =
            top.coerceIn(
                0,
                bitmap.height - 1
            )

        right =
            right.coerceIn(
                left + 1,
                bitmap.width
            )

        bottom =
            bottom.coerceIn(
                top + 1,
                bitmap.height
            )


        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )
    }

    // =============================================================
    // ROW LAYOUT
    // =============================================================

    private fun createRowsLayout(
        rowPattern: List<Int>,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ): List<RectF> {

        if (rowPattern.isEmpty()) {
            return emptyList()
        }

        val rows =
            rowPattern.size

        val verticalGap =
            TILE_GAP *
                    (rows - 1)

        val rowHeight =
            (
                    height -
                            verticalGap
                    ) / rows

        val result =
            mutableListOf<RectF>()

        var currentTop =
            top

        rowPattern.forEach { columns ->

            if (columns <= 0) {
                return@forEach
            }

            val horizontalGap =
                TILE_GAP *
                        (columns - 1)

            val tileWidth =
                (
                        width -
                                horizontalGap
                        ) / columns

            for (column in 0 until columns) {

                val tileLeft =
                    left +
                            column *
                            (
                                    tileWidth +
                                            TILE_GAP
                                    )

                result.add(
                    RectF(
                        tileLeft,
                        currentTop,
                        tileLeft + tileWidth,
                        currentTop + rowHeight
                    )
                )
            }

            currentTop +=
                rowHeight +
                        TILE_GAP
        }

        return result
    }

    // =============================================================
    // DRAW PERSON TILE
    // =============================================================

    private fun drawPersonTile(
        canvas: Canvas,
        tileRect: RectF,
        face: DetectedFace,
        facesInFrame: Int
    ) {

        val crop =
            createFaceCenteredCrop(
                face = face,
                facesInFrame = facesInFrame
            )

        val destination =
            calculateCenterCropRect(
                sourceWidth = crop.width,
                sourceHeight = crop.height,
                destination = tileRect
            )

        canvas.save()

        val path =
            Path().apply {

                addRoundRect(
                    tileRect,
                    CORNER_RADIUS,
                    CORNER_RADIUS,
                    Path.Direction.CW
                )
            }

        canvas.clipPath(path)

        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                        Paint.FILTER_BITMAP_FLAG
            )

        canvas.drawBitmap(
            crop,
            null,
            destination,
            paint
        )

        canvas.restore()
    }

    // =============================================================
    // SELECT FINAL REPRESENTATIVE
    // =============================================================

    private fun selectPersonRepresentative(
        person: Person,
        faceCountAtTimestamp:
        Map<Long, Int>
    ): DetectedFace? {

        val candidates =
            person.appearances
                .mapNotNull {
                    it.representativeFace
                }

        if (candidates.isEmpty()) {

            return person.faces
                .firstOrNull()
        }

        return RepresentativeSelector()
            .selectBestFace(
                faces = candidates,
                faceCountAtTimestamp =
                    faceCountAtTimestamp
            )
    }

    // =============================================================
    // FACE-CENTRED PORTRAIT CROP
    // =============================================================

    private fun createFaceCenteredCrop(
        face: DetectedFace,
        facesInFrame: Int
    ): Bitmap {

        val bitmap =
            face.frame

        val box =
            face.boundingBox

        /*
         * ---------------------------------------------------------
         * Find the actual centre of the face.
         * ---------------------------------------------------------
         */

        val leftEye =
            face.leftEyePosition

        val rightEye =
            face.rightEyePosition

        val faceCenterX: Float
        val faceCenterY: Float

        if (
            leftEye != null &&
            rightEye != null
        ) {

            faceCenterX =
                (
                        leftEye.x +
                                rightEye.x
                        ) / 2f

            /*
             * Eyes are above the centre of the head.
             * Move the crop centre slightly downward so
             * the mouth and chin remain visible.
             */
            faceCenterY =
                (
                        leftEye.y +
                                rightEye.y
                        ) / 2f +
                        box.height() * 0.20f

        } else {

            faceCenterX =
                box.exactCenterX()

            faceCenterY =
                box.exactCenterY()
        }

        /*
         * ---------------------------------------------------------
         * Estimate real face width using eye distance.
         * ---------------------------------------------------------
         */

        val eyeDistance: Float =
            if (
                leftEye != null &&
                rightEye != null
            ) {

                val dx =
                    rightEye.x -
                            leftEye.x

                val dy =
                    rightEye.y -
                            leftEye.y

                sqrt(
                    dx * dx +
                            dy * dy
                )

            } else {

                box.width().toFloat()
            }

        /*
         * Estimate the full head width.
         */
        val estimatedFaceWidth =
            if (eyeDistance > 5f) {

                eyeDistance * 2.45f

            } else {

                box.width().toFloat()
            }

        /*
         * ---------------------------------------------------------
         * SQUARE CROP
         *
         * We want:
         *
         *       hair
         *    ┌───────────┐
         *    │           │
         *    │   FACE    │
         *    │           │
         *    │ shoulders │
         *    └───────────┘
         *
         * NOT:
         *
         *       👁️👁️
         *       👃
         *       👄
         * ---------------------------------------------------------
         */

        val cropSize =
            (
                    estimatedFaceWidth * 2.0f
                    )
                .coerceAtLeast(220f)
                .coerceAtMost(
                    bitmap.width * 0.60f
                )
                .coerceAtMost(
                    bitmap.height * 0.60f
                )

        /*
         * Shared frames get a slightly smaller crop
         * to avoid accidentally including another person.
         */
        val finalCropSize =
            if (facesInFrame > 1) {

                (
                        estimatedFaceWidth * 1.75f
                        )
                    .coerceAtLeast(220f)
                    .coerceAtMost(
                        bitmap.width * 0.48f
                    )
                    .coerceAtMost(
                        bitmap.height * 0.48f
                    )

            } else {

                cropSize
            }

        /*
         * ---------------------------------------------------------
         * Create square bounds.
         * ---------------------------------------------------------
         */

        var left =
            (
                    faceCenterX -
                            finalCropSize / 2f
                    ).toInt()

        var top =
            (
                    faceCenterY -
                            finalCropSize / 2f
                    ).toInt()

        var right =
            left +
                    finalCropSize.toInt()

        var bottom =
            top +
                    finalCropSize.toInt()

        /*
         * ---------------------------------------------------------
         * Keep crop inside bitmap.
         * ---------------------------------------------------------
         */

        if (left < 0) {

            right -= left
            left = 0
        }

        if (top < 0) {

            bottom -= top
            top = 0
        }

        if (right > bitmap.width) {

            val shift =
                right -
                        bitmap.width

            left -= shift
            right =
                bitmap.width
        }

        if (bottom > bitmap.height) {

            val shift =
                bottom -
                        bitmap.height

            top -= shift
            bottom =
                bitmap.height
        }

        /*
         * Final safety.
         */

        left =
            left.coerceIn(
                0,
                bitmap.width - 2
            )

        top =
            top.coerceIn(
                0,
                bitmap.height - 2
            )

        right =
            right.coerceIn(
                left + 2,
                bitmap.width
            )

        bottom =
            bottom.coerceIn(
                top + 2,
                bitmap.height
            )

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )
    }

    // =============================================================
    // CENTER CROP
    // =============================================================

    private fun calculateCenterCropRect(
        sourceWidth: Int,
        sourceHeight: Int,
        destination: RectF
    ): RectF {

        if (
            sourceWidth <= 0 ||
            sourceHeight <= 0
        ) {
            return destination
        }

        val sourceRatio =
            sourceWidth.toFloat() /
                    sourceHeight.toFloat()

        val destinationRatio =
            destination.width() /
                    destination.height()

        return if (
            sourceRatio > destinationRatio
        ) {

            val scaledHeight =
                destination.height()

            val scaledWidth =
                scaledHeight *
                        sourceRatio

            RectF(
                destination.centerX() -
                        scaledWidth / 2f,

                destination.top,

                destination.centerX() +
                        scaledWidth / 2f,

                destination.bottom
            )

        } else {

            val scaledWidth =
                destination.width()

            val scaledHeight =
                scaledWidth /
                        sourceRatio

            RectF(
                destination.left,

                destination.centerY() -
                        scaledHeight / 2f,

                destination.right,

                destination.centerY() +
                        scaledHeight / 2f
            )
        }
    }
}