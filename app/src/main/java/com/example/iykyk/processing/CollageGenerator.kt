package com.example.iykyk.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.iykyk.model.DetectedFace
import com.example.iykyk.model.Person
import kotlin.math.max
import kotlin.math.min

class CollageGenerator {

    companion object {
        private const val TILE_WIDTH = 600
        private const val TILE_HEIGHT = 750

        private const val COLUMNS = 2

        private const val OUTER_PADDING = 32
        private const val TILE_GAP = 24

        private const val TITLE_HEIGHT = 100
    }

    /**
     * Creates one collage containing exactly one
     * representative frame for every detected person.
     */
    fun createCollage(
        people: List<Person>
    ): Bitmap {

        if (people.isEmpty()) {
            return Bitmap.createBitmap(
                1,
                1,
                Bitmap.Config.ARGB_8888
            )
        }

        /*
         * One representative face per person.
         */
        val representatives =
            people.mapNotNull { person ->

                val representative =
                    selectPersonRepresentative(
                        person
                    )

                if (representative != null) {
                    person.id to representative
                } else {
                    null
                }
            }

        if (representatives.isEmpty()) {
            return Bitmap.createBitmap(
                1,
                1,
                Bitmap.Config.ARGB_8888
            )
        }

        val rows =
            (representatives.size +
                    COLUMNS - 1) /
                    COLUMNS

        val collageWidth =
            OUTER_PADDING * 2 +
                    COLUMNS * TILE_WIDTH +
                    (COLUMNS - 1) *
                    TILE_GAP

        val collageHeight =
            TITLE_HEIGHT +
                    OUTER_PADDING * 2 +
                    rows * TILE_HEIGHT +
                    (rows - 1) *
                    TILE_GAP

        val collage =
            Bitmap.createBitmap(
                collageWidth,
                collageHeight,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(collage)

        /*
         * Background.
         */
        canvas.drawColor(
            android.graphics.Color.WHITE
        )

        /*
         * Title.
         */
        val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {

                color =
                    android.graphics.Color.BLACK

                textSize = 42f

                typeface =
                    android.graphics.Typeface
                        .create(
                            android.graphics.Typeface.DEFAULT,
                            android.graphics.Typeface.BOLD
                        )
            }

        canvas.drawText(
            "IYKYK",
            OUTER_PADDING.toFloat(),
            62f,
            titlePaint
        )

        /*
         * Subtitle.
         */
        val subtitlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {

                color =
                    android.graphics.Color.DKGRAY

                textSize = 24f
            }

        canvas.drawText(
            "${representatives.size} people",
            OUTER_PADDING.toFloat(),
            88f,
            subtitlePaint
        )

        /*
         * Draw one tile per person.
         */
        representatives.forEachIndexed {
                index,
                pair ->

            val personId =
                pair.first

            val face =
                pair.second

            val column =
                index % COLUMNS

            val row =
                index / COLUMNS

            val left =
                OUTER_PADDING +
                        column *
                        (TILE_WIDTH +
                                TILE_GAP)

            val top =
                TITLE_HEIGHT +
                        OUTER_PADDING +
                        row *
                        (TILE_HEIGHT +
                                TILE_GAP)

            val tileRect =
                RectF(
                    left.toFloat(),
                    top.toFloat(),
                    (left +
                            TILE_WIDTH).toFloat(),
                    (top +
                            TILE_HEIGHT).toFloat()
                )

            /*
             * Crop generously around the face.
             */
            val crop =
                createGenerousPortraitCrop(
                    face
                )

            /*
             * Scale crop while preserving aspect ratio.
             */
            val destination =
                calculateCenterCropRect(
                    crop.width,
                    crop.height,
                    tileRect
                )

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

            /*
             * Person label.
             */
            val labelBackground =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {

                    color =
                        android.graphics.Color
                            .argb(
                                190,
                                0,
                                0,
                                0
                            )
                }

            canvas.drawRect(
                tileRect.left,
                tileRect.bottom - 58f,
                tileRect.right,
                tileRect.bottom,
                labelBackground
            )

            val labelPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {

                    color =
                        android.graphics.Color.WHITE

                    textSize = 26f

                    typeface =
                        android.graphics.Typeface
                            .DEFAULT_BOLD
                }

            canvas.drawText(
                "Person $personId",
                tileRect.left + 18f,
                tileRect.bottom - 21f,
                labelPaint
            )
        }

        return collage
    }

    /**
     * Select the best representative frame for
     * this person from all of their appearances.
     *
     * Each appearance already has a good candidate.
     * We run RepresentativeSelector once more over
     * those candidates to select the person's final
     * best frame.
     */
    private fun selectPersonRepresentative(
        person: Person
    ): DetectedFace? {

        val candidates =
            person.appearances.mapNotNull {
                it.representativeFace
            }

        if (candidates.isEmpty()) {
            return null
        }

        val selector =
            RepresentativeSelector()

        return selector.selectBestFace(
            candidates
        )
    }

    /**
     * Creates a generous portrait crop around the
     * detected face.
     *
     * This deliberately includes substantial context
     * around the face instead of tightly cropping to
     * the face bounding box.
     */
    private fun createGenerousPortraitCrop(
        face: DetectedFace
    ): Bitmap {

        val bitmap =
            face.frame

        val box =
            face.boundingBox

        /*
         * Generous horizontal and vertical margins.
         *
         * This gives us:
         * - head
         * - hair
         * - shoulders
         * - surrounding context
         */
        val marginX =
            (box.width() * 0.55f)
                .toInt()

        val marginY =
            (box.height() * 1.95f)
                .toInt()

        var left =
            box.centerX() -
                    box.width() / 2 -
                    marginX

        var top =
            box.centerY() -
                    box.height() / 2 -
                    marginY

        var right =
            box.centerX() +
                    box.width() / 2 +
                    marginX

        var bottom =
            box.centerY() +
                    box.height() / 2 +
                    marginY

        /*
         * Clamp to the actual video frame.
         */
        left =
            left.coerceAtLeast(0)

        top =
            top.coerceAtLeast(0)

        right =
            right.coerceAtMost(
                bitmap.width
            )

        bottom =
            bottom.coerceAtMost(
                bitmap.height
            )

        var width =
            right - left

        var height =
            bottom - top

        if (width <= 0 || height <= 0) {
            return bitmap
        }

        /*
         * Final safety bounds.
         */
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

        width =
            right - left

        height =
            bottom - top

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            width,
            height
        )
    }

    /**
     * Calculates a center-crop destination rectangle
     * so the source image fills the tile without
     * distortion.
     */
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

            /*
             * Source is wider.
             */
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

            /*
             * Source is taller.
             */
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