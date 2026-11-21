package com.example.stitching

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.example.data.model.StitchDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SeamJoin(
    val leftFrameIndex: Int,
    val rightFrameIndex: Int,
    val overlapPx: Int,
    val headerCropPx: Int,
    val footerCropPx: Int,
    val confidence: Float,
    val isDuplicate: Boolean = false
)

data class StitchPlan(
    val direction: StitchDirection,
    val framePaths: List<String>,
    val joins: List<SeamJoin>,
    val frameWidth: Int,
    val frameHeight: Int
) {
    val hasLowConfidenceSeam: Boolean
        get() = joins.any { !it.isDuplicate && it.confidence < 0.65f }

    val activeFrameCount: Int
        get() = 1 + joins.count { !it.isDuplicate }
}

sealed interface StitchResult {
    data class Success(
        val bitmap: Bitmap,
        val plan: StitchPlan
    ) : StitchResult

    data class Failure(
        val message: String,
        val partialPlan: StitchPlan? = null
    ) : StitchResult
}

class ImageStitcher {

    suspend fun buildStitchPlan(
        framePaths: List<String>,
        direction: StitchDirection,
        overlapRange: Pair<Float, Float>,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): StitchPlan = withContext(Dispatchers.Default) {
        if (framePaths.isEmpty()) {
            return@withContext StitchPlan(direction, emptyList(), emptyList(), 0, 0)
        }
        val firstBounds = decodeBounds(framePaths.first())
        if (framePaths.size == 1) {
            return@withContext StitchPlan(direction, framePaths, emptyList(), firstBounds.first, firstBounds.second)
        }

        val joins = mutableListOf<SeamJoin>()
        var prevIndex = 0
        var prevBmp = decodeBitmap(framePaths[0])

        for (i in 1 until framePaths.size) {
            onProgress(i.toFloat() / framePaths.size, "Analyzing frame ${i + 1} of ${framePaths.size}")
            val currBmp = decodeBitmap(framePaths[i])
            if (prevBmp == null || currBmp == null) {
                joins.add(
                    SeamJoin(
                        leftFrameIndex = prevIndex,
                        rightFrameIndex = i,
                        overlapPx = 0,
                        headerCropPx = 0,
                        footerCropPx = 0,
                        confidence = 0f,
                        isDuplicate = false
                    )
                )
                prevBmp?.recycle()
                prevBmp = currBmp
                prevIndex = i
                continue
            }

            val join = if (direction == StitchDirection.VERTICAL) {
                analyzeVerticalPair(prevIndex, i, prevBmp, currBmp, overlapRange)
            } else {
                analyzeHorizontalPair(prevIndex, i, prevBmp, currBmp, overlapRange)
            }

            joins.add(join)
            if (join.isDuplicate) {
                // Keep prevBmp as reference and discard duplicate currBmp
                currBmp.recycle()
            } else {
                prevBmp.recycle()
                prevBmp = currBmp
                prevIndex = i
            }
        }
        prevBmp?.recycle()

        StitchPlan(
            direction = direction,
            framePaths = framePaths,
            joins = joins,
            frameWidth = firstBounds.first,
            frameHeight = firstBounds.second
        )
    }

    suspend fun renderStitchPlan(
        plan: StitchPlan,
        maxOutputLengthPx: Int,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): StitchResult = withContext(Dispatchers.Default) {
        if (plan.framePaths.isEmpty()) {
            return@withContext StitchResult.Failure("No frames available to stitch.")
        }
        if (plan.framePaths.size == 1) {
            val single = decodeBitmap(plan.framePaths.first())
                ?: return@withContext StitchResult.Failure("Failed to decode captured frame.")
            return@withContext StitchResult.Success(single, plan)
        }

        return@withContext try {
            if (plan.direction == StitchDirection.VERTICAL) {
                renderVertical(plan, maxOutputLengthPx, onProgress)
            } else {
                renderHorizontal(plan, maxOutputLengthPx, onProgress)
            }
        } catch (oom: OutOfMemoryError) {
            StitchResult.Failure("Out of memory while stitching large image. Try lowering Max Length in Settings.", plan)
        } catch (t: Throwable) {
            StitchResult.Failure(t.message ?: "Unexpected stitching failure.", plan)
        }
    }

    private fun renderVertical(
        plan: StitchPlan,
        maxOutputLengthPx: Int,
        onProgress: (Float, String) -> Unit
    ): StitchResult {
        val activeFrames = mutableListOf<Pair<Int, SeamJoin?>>()
        activeFrames.add(0 to null)
        for (join in plan.joins) {
            if (!join.isDuplicate) {
                activeFrames.add(join.rightFrameIndex to join)
            }
        }

        val boundsList = activeFrames.map { decodeBounds(plan.framePaths[it.first]) }
        val targetWidth = boundsList.minOf { it.first }.coerceAtLeast(1)

        // Calculate exact height per slice
        data class SliceSpec(val frameIdx: Int, val srcTop: Int, val srcBottom: Int)
        val slices = mutableListOf<SliceSpec>()
        var totalHeight = 0

        for (idx in activeFrames.indices) {
            val (frameIdx, join) = activeFrames[idx]
            val frameH = boundsList[idx].second
            val isFirst = idx == 0
            val isLast = idx == activeFrames.lastIndex

            val nextJoin = if (!isLast) activeFrames[idx + 1].second else null
            val bottomCrop = if (!isLast) (nextJoin?.footerCropPx ?: 0) else 0
            val topCrop = if (isFirst) 0 else ((join?.headerCropPx ?: 0) + (join?.overlapPx ?: 0))

            val safeTop = topCrop.coerceIn(0, frameH)
            val safeBottom = (frameH - bottomCrop).coerceIn(safeTop, frameH)
            val sliceH = (safeBottom - safeTop).coerceAtLeast(0)

            if (sliceH > 0) {
                val allowed = (maxOutputLengthPx - totalHeight).coerceAtLeast(0)
                if (allowed <= 0) break
                val clampedH = min(sliceH, allowed)
                slices.add(SliceSpec(frameIdx, safeTop, safeTop + clampedH))
                totalHeight += clampedH
            }
        }

        if (totalHeight <= 0) {
            return StitchResult.Failure("Calculated stitched height was zero.", plan)
        }

        val output = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        var cursorY = 0

        slices.forEachIndexed { idx, slice ->
            onProgress(idx.toFloat() / slices.size, "Stitching section ${idx + 1}/${slices.size}")
            val bmp = decodeBitmap(plan.framePaths[slice.frameIdx]) ?: return@forEachIndexed
            val sliceH = slice.srcBottom - slice.srcTop
            val srcW = min(bmp.width, targetWidth)
            val srcRect = Rect(0, slice.srcTop, srcW, slice.srcBottom)
            val dstRect = Rect(0, cursorY, targetWidth, cursorY + sliceH)
            canvas.drawBitmap(bmp, srcRect, dstRect, null)
            cursorY += sliceH
            bmp.recycle()
        }

        onProgress(1f, "Stitch complete")
        return StitchResult.Success(output, plan)
    }

    private fun renderHorizontal(
        plan: StitchPlan,
        maxOutputLengthPx: Int,
        onProgress: (Float, String) -> Unit
    ): StitchResult {
        val activeFrames = mutableListOf<Pair<Int, SeamJoin?>>()
        activeFrames.add(0 to null)
        for (join in plan.joins) {
            if (!join.isDuplicate) {
                activeFrames.add(join.rightFrameIndex to join)
            }
        }

        val boundsList = activeFrames.map { decodeBounds(plan.framePaths[it.first]) }
        val targetHeight = boundsList.minOf { it.second }.coerceAtLeast(1)

        data class SliceSpec(val frameIdx: Int, val srcLeft: Int, val srcRight: Int)
        val slices = mutableListOf<SliceSpec>()
        var totalWidth = 0

        for (idx in activeFrames.indices) {
            val (frameIdx, join) = activeFrames[idx]
            val frameW = boundsList[idx].first
            val isFirst = idx == 0
            val isLast = idx == activeFrames.lastIndex

            val nextJoin = if (!isLast) activeFrames[idx + 1].second else null
            val rightCrop = if (!isLast) (nextJoin?.footerCropPx ?: 0) else 0
            val leftCrop = if (isFirst) 0 else ((join?.headerCropPx ?: 0) + (join?.overlapPx ?: 0))

            val safeLeft = leftCrop.coerceIn(0, frameW)
            val safeRight = (frameW - rightCrop).coerceIn(safeLeft, frameW)
            val sliceW = (safeRight - safeLeft).coerceAtLeast(0)

            if (sliceW > 0) {
                val allowed = (maxOutputLengthPx - totalWidth).coerceAtLeast(0)
                if (allowed <= 0) break
                val clampedW = min(sliceW, allowed)
                slices.add(SliceSpec(frameIdx, safeLeft, safeLeft + clampedW))
                totalWidth += clampedW
            }
        }

        if (totalWidth <= 0) {
            return StitchResult.Failure("Calculated stitched width was zero.", plan)
        }

        val output = Bitmap.createBitmap(totalWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        var cursorX = 0

        slices.forEachIndexed { idx, slice ->
            onProgress(idx.toFloat() / slices.size, "Stitching section ${idx + 1}/${slices.size}")
            val bmp = decodeBitmap(plan.framePaths[slice.frameIdx]) ?: return@forEachIndexed
            val sliceW = slice.srcRight - slice.srcLeft
            val srcH = min(bmp.height, targetHeight)
            val srcRect = Rect(slice.srcLeft, 0, slice.srcRight, srcH)
            val dstRect = Rect(cursorX, 0, cursorX + sliceW, targetHeight)
            canvas.drawBitmap(bmp, srcRect, dstRect, null)
            cursorX += sliceW
            bmp.recycle()
        }

        onProgress(1f, "Stitch complete")
        return StitchResult.Success(output, plan)
    }

    internal fun analyzeVerticalPair(
        leftIdx: Int,
        rightIdx: Int,
        bmpA: Bitmap,
        bmpB: Bitmap,
        overlapRange: Pair<Float, Float>
    ): SeamJoin {
        val w = min(bmpA.width, bmpB.width)
        val h = min(bmpA.height, bmpB.height)
        if (w < 16 || h < 64) {
            return SeamJoin(leftIdx, rightIdx, 0, 0, 0, 0f)
        }

        // Sample columns excluding extreme left/right 4% (scrollbars/edge shadows)
        val xStart = (w * 0.04f).toInt()
        val xEnd = (w * 0.94f).toInt().coerceAtLeast(xStart + 8)
        val xStepCoarse = max(4, (xEnd - xStart) / 48)
        val sampleCols = (xStart until xEnd step xStepCoarse).toList()

        // 1. Detect static top header (status bar + fixed toolbar)
        val maxHeaderCheck = (h * 0.28f).toInt()
        var staticTop = 0
        for (y in 0 until maxHeaderCheck step 2) {
            val mad = rowMad(bmpA, bmpB, y, y, sampleCols)
            if (mad < 6.5f) {
                staticTop = y + 2
            } else {
                break
            }
        }
        if (staticTop < 12) staticTop = 0

        // 2. Detect static bottom footer (navigation bar + bottom bar)
        val maxFooterCheck = (h * 0.22f).toInt()
        var staticBottom = 0
        for (offset in 0 until maxFooterCheck step 2) {
            val y = h - 1 - offset
            val mad = rowMad(bmpA, bmpB, y, y, sampleCols)
            if (mad < 6.5f) {
                staticBottom = offset + 2
            } else {
                break
            }
        }
        if (staticBottom < 12) staticBottom = 0

        val activeTop = staticTop
        val activeBottom = (h - staticBottom).coerceAtLeast(activeTop + 48)
        val activeH = activeBottom - activeTop

        // 3. Check duplicate frame in active region
        val dupMad = regionMad(bmpA, bmpB, activeTop, activeTop, activeH, sampleCols, rowStep = max(4, activeH / 32))
        if (dupMad < 3.2f) {
            return SeamJoin(
                leftFrameIndex = leftIdx,
                rightFrameIndex = rightIdx,
                overlapPx = activeH,
                headerCropPx = staticTop,
                footerCropPx = staticBottom,
                confidence = 1f,
                isDuplicate = true
            )
        }

        // 4. Coarse search in preferred overlap range first, then broader fallback if low confidence
        val minOv = max(24, (activeH * overlapRange.first).toInt())
        val maxOv = min(activeH - 24, (activeH * overlapRange.second).toInt()).coerceAtLeast(minOv + 8)

        var bestCandidate = findBestVerticalOverlap(
            bmpA, bmpB, activeTop, activeBottom, minOv, maxOv, sampleCols
        )

        if (bestCandidate.second < 0.72f) {
            val wideMin = max(20, (activeH * 0.05f).toInt())
            val wideMax = min(activeH - 16, (activeH * 0.88f).toInt())
            val fallback = findBestVerticalOverlap(
                bmpA, bmpB, activeTop, activeBottom, wideMin, wideMax, sampleCols
            )
            if (fallback.second > bestCandidate.second) {
                bestCandidate = fallback
            }
        }

        return SeamJoin(
            leftFrameIndex = leftIdx,
            rightFrameIndex = rightIdx,
            overlapPx = bestCandidate.first,
            headerCropPx = staticTop,
            footerCropPx = staticBottom,
            confidence = bestCandidate.second,
            isDuplicate = false
        )
    }

    private fun findBestVerticalOverlap(
        bmpA: Bitmap,
        bmpB: Bitmap,
        activeTop: Int,
        activeBottom: Int,
        minOv: Int,
        maxOv: Int,
        sampleCols: List<Int>
    ): Pair<Int, Float> {
        val coarseCandidates = mutableListOf<Pair<Int, Float>>() // (overlap, score)
        for (ov in minOv..maxOv step 4) {
            val aStart = activeBottom - ov
            val bStart = activeTop
            val rowStep = max(2, ov / 36)
            val (mad, variance) = stripMadAndVariance(bmpA, bmpB, aStart, bStart, ov, sampleCols, rowStep)
            // Penalize blank/uniform strips slightly so textured content anchors the seam
            val textureFactor = if (variance < 3.5f) 1.6f else 1.0f
            val score = mad * textureFactor
            coarseCandidates.add(ov to score)
        }

        if (coarseCandidates.isEmpty()) return 0 to 0f

        val topCoarse = coarseCandidates.sortedBy { it.second }.take(3)
        val fineCols = (sampleCols.first()..sampleCols.last() step max(2, (sampleCols.last() - sampleCols.first()) / 80)).toList()

        var bestOv = topCoarse.first().first
        var bestMad = Float.MAX_VALUE

        for ((coarseOv, _) in topCoarse) {
            val start = max(minOv, coarseOv - 6)
            val end = min(maxOv, coarseOv + 6)
            for (ov in start..end) {
                val aStart = activeBottom - ov
                val bStart = activeTop
                val rowStep = max(1, ov / 80)
                val (mad, variance) = stripMadAndVariance(bmpA, bmpB, aStart, bStart, ov, fineCols, rowStep)
                val adjustedMad = if (variance < 2.5f) mad + 4f else mad
                if (adjustedMad < bestMad) {
                    bestMad = adjustedMad
                    bestOv = ov
                }
            }
        }

        val confidence = (1f - (bestMad / 42f)).coerceIn(0f, 1f)
        return bestOv to confidence
    }

    internal fun analyzeHorizontalPair(
        leftIdx: Int,
        rightIdx: Int,
        bmpA: Bitmap,
        bmpB: Bitmap,
        overlapRange: Pair<Float, Float>
    ): SeamJoin {
        val w = min(bmpA.width, bmpB.width)
        val h = min(bmpA.height, bmpB.height)
        if (w < 64 || h < 16) {
            return SeamJoin(leftIdx, rightIdx, 0, 0, 0, 0f)
        }

        val yStart = (h * 0.05f).toInt()
        val yEnd = (h * 0.95f).toInt().coerceAtLeast(yStart + 8)
        val yStep = max(4, (yEnd - yStart) / 48)
        val sampleRows = (yStart until yEnd step yStep).toList()

        val minOv = max(24, (w * overlapRange.first).toInt())
        val maxOv = min(w - 24, (w * overlapRange.second).toInt()).coerceAtLeast(minOv + 8)

        // Check duplicate
        val dupMad = horizontalRegionMad(bmpA, bmpB, 0, 0, w, sampleRows, max(4, w / 32))
        if (dupMad < 3.2f) {
            return SeamJoin(leftIdx, rightIdx, w, 0, 0, 1f, isDuplicate = true)
        }

        var bestOv = minOv
        var bestMad = Float.MAX_VALUE
        for (ov in minOv..maxOv step 4) {
            val aStart = w - ov
            val colStep = max(2, ov / 36)
            val mad = horizontalRegionMad(bmpA, bmpB, aStart, 0, ov, sampleRows, colStep)
            if (mad < bestMad) {
                bestMad = mad
                bestOv = ov
            }
        }

        val fineStart = max(minOv, bestOv - 6)
        val fineEnd = min(maxOv, bestOv + 6)
        for (ov in fineStart..fineEnd) {
            val aStart = w - ov
            val colStep = max(1, ov / 72)
            val mad = horizontalRegionMad(bmpA, bmpB, aStart, 0, ov, sampleRows, colStep)
            if (mad < bestMad) {
                bestMad = mad
                bestOv = ov
            }
        }

        val confidence = (1f - (bestMad / 42f)).coerceIn(0f, 1f)
        return SeamJoin(leftIdx, rightIdx, bestOv, 0, 0, confidence, isDuplicate = false)
    }

    private fun rowMad(bmpA: Bitmap, bmpB: Bitmap, yA: Int, yB: Int, cols: List<Int>): Float {
        var sum = 0L
        for (x in cols) {
            val pA = bmpA.getPixel(x, yA)
            val pB = bmpB.getPixel(x, yB)
            sum += abs(Color.red(pA) - Color.red(pB)) +
                abs(Color.green(pA) - Color.green(pB)) +
                abs(Color.blue(pA) - Color.blue(pB))
        }
        return sum.toFloat() / (cols.size * 3f)
    }

    private fun regionMad(
        bmpA: Bitmap,
        bmpB: Bitmap,
        startA: Int,
        startB: Int,
        height: Int,
        cols: List<Int>,
        rowStep: Int
    ): Float {
        var sum = 0L
        var count = 0
        var offset = 0
        while (offset < height) {
            val yA = startA + offset
            val yB = startB + offset
            for (x in cols) {
                val pA = bmpA.getPixel(x, yA)
                val pB = bmpB.getPixel(x, yB)
                sum += abs(Color.red(pA) - Color.red(pB)) +
                    abs(Color.green(pA) - Color.green(pB)) +
                    abs(Color.blue(pA) - Color.blue(pB))
                count++
            }
            offset += rowStep
        }
        return if (count == 0) 255f else sum.toFloat() / (count * 3f)
    }

    private fun stripMadAndVariance(
        bmpA: Bitmap,
        bmpB: Bitmap,
        startA: Int,
        startB: Int,
        height: Int,
        cols: List<Int>,
        rowStep: Int
    ): Pair<Float, Float> {
        var diffSum = 0L
        var lumSum = 0L
        var lumSqSum = 0L
        var count = 0
        var offset = 0
        while (offset < height) {
            val yA = startA + offset
            val yB = startB + offset
            for (x in cols) {
                val pA = bmpA.getPixel(x, yA)
                val pB = bmpB.getPixel(x, yB)
                val rA = Color.red(pA)
                val gA = Color.green(pA)
                val bA = Color.blue(pA)
                diffSum += abs(rA - Color.red(pB)) +
                    abs(gA - Color.green(pB)) +
                    abs(bA - Color.blue(pB))
                val lum = (rA + gA + bA) / 3
                lumSum += lum
                lumSqSum += (lum * lum).toLong()
                count++
            }
            offset += rowStep
        }
        if (count == 0) return 255f to 0f
        val mad = diffSum.toFloat() / (count * 3f)
        val mean = lumSum.toDouble() / count
        val variance = sqrt(max(0.0, (lumSqSum.toDouble() / count) - mean * mean)).toFloat()
        return mad to variance
    }

    private fun horizontalRegionMad(
        bmpA: Bitmap,
        bmpB: Bitmap,
        startXA: Int,
        startXB: Int,
        width: Int,
        rows: List<Int>,
        colStep: Int
    ): Float {
        var sum = 0L
        var count = 0
        var offset = 0
        while (offset < width) {
            val xA = startXA + offset
            val xB = startXB + offset
            for (y in rows) {
                val pA = bmpA.getPixel(xA, y)
                val pB = bmpB.getPixel(xB, y)
                sum += abs(Color.red(pA) - Color.red(pB)) +
                    abs(Color.green(pA) - Color.green(pB)) +
                    abs(Color.blue(pA) - Color.blue(pB))
                count++
            }
            offset += colStep
        }
        return if (count == 0) 255f else sum.toFloat() / (count * 3f)
    }

    fun decodeBitmap(path: String): Bitmap? {
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    fun decodeBounds(path: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        return opts.outWidth.coerceAtLeast(1) to opts.outHeight.coerceAtLeast(1)
    }
}
