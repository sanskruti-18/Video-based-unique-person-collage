package com.example.iykyk.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.iykyk.model.DetectedFace
import com.example.iykyk.model.Person

class CollageGenerator {

    companion object {
        private const val STORY_WIDTH = 1080
        private const val STORY_HEIGHT = 1920

        private const val OUTER_PADDING = 48f
        private const val TILE_GAP = 28f
        private const val HEADER_HEIGHT = 200f
        private const val FOOTER_HEIGHT = 90f
        private const val CORNER_RADIUS = 32f
    }

    /**
     * Creates one Instagram Story-style collage containing exactly one
     * representative frame for every detected person.
     */
    fun createCollage(
        people: List<Person>,
        faceCountAtTimestamp: Map<Long, Int> = emptyMap()
    ): Bitmap {

        if (people.isEmpty()) {
            return Bitmap.createBitmap(
                STORY_WIDTH,
                STORY_HEIGHT,
                Bitmap.Config.ARGB_8888
            ).apply {
                eraseColor(android.graphics.Color.DKGRAY)
            }
        }

        // Select the best representative face for each person
        val representatives = people.mapNotNull { person ->
            val representative = selectPersonRepresentative(person, faceCountAtTimestamp)
            if (representative != null) {
                Triple(person.id, person.appearanceCount, representative)
            } else {
                null
            }
        }

        if (representatives.isEmpty()) {
            return Bitmap.createBitmap(
                STORY_WIDTH,
                STORY_HEIGHT,
                Bitmap.Config.ARGB_8888
            ).apply {
                eraseColor(android.graphics.Color.DKGRAY)
            }
        }

        val collage = Bitmap.createBitmap(
            STORY_WIDTH,
            STORY_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(collage)

        // Background: Modern sleek dark charcoal/slate (#0F172A)
        canvas.drawColor(android.graphics.Color.rgb(15, 23, 42))

        // Total appearances across all detected persons
        val totalAppearances = people.sumOf { it.appearanceCount }

        // Draw Header
        drawHeader(canvas, people.size, totalAppearances)

        // Grid dimensions
        val columns = if (representatives.size <= 2) 1 else 2
        val rows = (representatives.size + columns - 1) / columns

        val availableWidth = STORY_WIDTH - (OUTER_PADDING * 2) - ((columns - 1) * TILE_GAP)
        val tileWidth = availableWidth / columns

        val availableHeight = STORY_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT - (OUTER_PADDING * 2) - ((rows - 1) * TILE_GAP)
        val tileHeight = (availableHeight / rows).coerceAtLeast(150f)

        // Draw each person's tile
        representatives.forEachIndexed { index, triple ->
            val personId = triple.first
            val appearanceCount = triple.second
            val face = triple.third

            val row = index / columns
            val isLastOddItem = (index == representatives.size - 1) && (representatives.size % 2 != 0) && (columns == 2)

            val left = if (isLastOddItem) {
                // Center the last odd person
                (STORY_WIDTH - tileWidth) / 2f
            } else {
                val column = index % columns
                OUTER_PADDING + column * (tileWidth + TILE_GAP)
            }

            val top = HEADER_HEIGHT + OUTER_PADDING + row * (tileHeight + TILE_GAP)

            val tileRect = RectF(
                left,
                top,
                left + tileWidth,
                top + tileHeight
            )

            drawPersonTile(
                canvas = canvas,
                tileRect = tileRect,
                face = face,
                personId = personId,
                appearanceCount = appearanceCount
            )
        }

        // Draw Footer
        drawFooter(canvas)

        return collage
    }

    private fun drawHeader(canvas: Canvas, peopleCount: Int, totalAppearances: Int) {
        val topPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 41, 59)
            style = Paint.Style.FILL
        }
        val pillRect = RectF(OUTER_PADDING, 48f, OUTER_PADDING + 220f, 92f)
        canvas.drawRoundRect(pillRect, 22f, 22f, topPillPaint)

        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(56, 189, 248) // Sky blue
            textSize = 22f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("IYKYK COLLAGE", OUTER_PADDING + 24f, 78f, pillTextPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 52f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("Unique Faces", OUTER_PADDING, 150f, titlePaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(148, 163, 184) // Slate 400
            textSize = 26f
        }
        canvas.drawText(
            "$peopleCount unique people  •  $totalAppearances appearances",
            OUTER_PADDING,
            188f,
            subtitlePaint
        )
    }

    private fun drawFooter(canvas: Canvas) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(100, 116, 139)
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Processed on-device with ML Kit & MobileFaceNet",
            STORY_WIDTH / 2f,
            STORY_HEIGHT - 38f,
            footerPaint
        )
    }

    private fun drawPersonTile(
        canvas: Canvas,
        tileRect: RectF,
        face: DetectedFace,
        personId: Int,
        appearanceCount: Int
    ) {
        val generousCrop = createGenerousPortraitCrop(face)

        val destination = calculateCenterCropRect(
            sourceWidth = generousCrop.width,
            sourceHeight = generousCrop.height,
            destination = tileRect
        )

        // Clip tile with rounded corners
        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(tileRect, CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(generousCrop, null, destination, bitmapPaint)

        // Subtle gradient overlay at bottom for badge readability
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                tileRect.left,
                tileRect.bottom - 160f,
                tileRect.left,
                tileRect.bottom,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.argb(200, 0, 0, 0),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(
            tileRect.left,
            tileRect.bottom - 160f,
            tileRect.right,
            tileRect.bottom,
            gradientPaint
        )

        // Floating pill badge
        val badgeHeight = 64f
        val badgeMargin = 16f
        val badgeRect = RectF(
            tileRect.left + badgeMargin,
            tileRect.bottom - badgeHeight - badgeMargin,
            tileRect.right - badgeMargin,
            tileRect.bottom - badgeMargin
        )

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(220, 15, 23, 42)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, 18f, 18f, badgeBgPaint)

        // Person ID label
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(
            "Person $personId",
            badgeRect.left + 20f,
            badgeRect.centerY() + 9f,
            namePaint
        )

        // Appearance count label
        val countText = if (appearanceCount == 1) "1 appearance" else "$appearanceCount appearances"
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(56, 189, 248) // Vibrant sky blue
            textSize = 22f
            textAlign = Paint.Align.RIGHT
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            countText,
            badgeRect.right - 20f,
            badgeRect.centerY() + 8f,
            countPaint
        )

        canvas.restore()

        // Subtle sleek outer border around tile
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(50, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(tileRect, CORNER_RADIUS, CORNER_RADIUS, borderPaint)
    }

    /**
     * Select the best representative frame for this person from all their appearances.
     */
    private fun selectPersonRepresentative(
        person: Person,
        faceCountAtTimestamp: Map<Long, Int>
    ): DetectedFace? {
        val candidates = person.appearances.mapNotNull { it.representativeFace }
        if (candidates.isEmpty()) {
            return person.faces.firstOrNull()
        }

        val selector = RepresentativeSelector()
        return selector.selectBestFace(candidates, faceCountAtTimestamp)
            ?: candidates.firstOrNull()
    }

    /**
     * Creates a generous portrait crop around the detected face.
     * Includes head, hair, shoulders, and contextual background.
     */
    private fun createGenerousPortraitCrop(face: DetectedFace): Bitmap {
        val bitmap = face.frame
        val box = face.boundingBox

        val faceW = box.width()
        val faceH = box.height()

        // Generous portrait framing:
        // Horizontal: ~70% face width on both sides
        // Top (headroom): ~65% face height above forehead
        // Bottom (torso/shoulders): ~110% face height below chin
        val marginX = (faceW * 0.70f).toInt()
        val marginTop = (faceH * 0.65f).toInt()
        val marginBottom = (faceH * 1.10f).toInt()

        val left = (box.left - marginX).coerceAtLeast(0)
        val top = (box.top - marginTop).coerceAtLeast(0)
        val right = (box.right + marginX).coerceAtMost(bitmap.width)
        val bottom = (box.bottom + marginBottom).coerceAtMost(bitmap.height)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            return bitmap
        }

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            width,
            height
        )
    }

    /**
     * Calculates a center-crop destination rectangle so the source fills the tile without distortion.
     */
    private fun calculateCenterCropRect(
        sourceWidth: Int,
        sourceHeight: Int,
        destination: RectF
    ): RectF {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return destination
        }

        val sourceRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
        val destinationRatio = destination.width() / destination.height()

        return if (sourceRatio > destinationRatio) {
            val scaledHeight = destination.height()
            val scaledWidth = scaledHeight * sourceRatio
            RectF(
                destination.centerX() - scaledWidth / 2f,
                destination.top,
                destination.centerX() + scaledWidth / 2f,
                destination.bottom
            )
        } else {
            val scaledWidth = destination.width()
            val scaledHeight = scaledWidth / sourceRatio
            RectF(
                destination.left,
                destination.centerY() - scaledHeight / 2f,
                destination.right,
                destination.centerY() + scaledHeight / 2f
            )
        }
    }
}