package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.StitchDirection
import com.example.stitching.ImageStitcher
import com.example.stitching.StitchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageStitcherTest {

    private fun savePng(dir: File, name: String, bmp: Bitmap): String {
        val f = File(dir, name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private fun makeScrollingDocumentBitmap(width: Int, totalHeight: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { isAntiAlias = true }
        for (y in 0 until totalHeight step 28) {
            val hue = ((y * 13) % 360).toFloat()
            paint.color = Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.85f))
            canvas.drawRect(24f, y.toFloat(), (width - 24).toFloat(), (y + 18).toFloat(), paint)
            paint.color = Color.DKGRAY
            val barWidth = 60 + ((y * 7) % (width - 120))
            canvas.drawRect(36f, (y + 4).toFloat(), (36 + barWidth).toFloat(), (y + 12).toFloat(), paint)
        }
        return bmp
    }

    @Test
    fun `vertical stitch removes static header footer and exact overlap`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "stitch_test_${System.currentTimeMillis()}").apply { mkdirs() }

        val w = 360
        val h = 600
        val headerH = 60
        val footerH = 48
        val activeH = h - headerH - footerH // 492
        val overlap = 140
        val scrollDelta = activeH - overlap // 352

        val doc = makeScrollingDocumentBitmap(w, 1200)

        fun buildFrame(docStartY: Int): Bitmap {
            val f = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(f)
            val p = Paint()
            p.color = Color.rgb(15, 23, 42)
            c.drawRect(0f, 0f, w.toFloat(), headerH.toFloat(), p)
            val slice = Bitmap.createBitmap(doc, 0, docStartY, w, activeH)
            c.drawBitmap(slice, 0f, headerH.toFloat(), null)
            p.color = Color.rgb(30, 41, 59)
            c.drawRect(0f, (h - footerH).toFloat(), w.toFloat(), h.toFloat(), p)
            slice.recycle()
            return f
        }

        val frame0 = buildFrame(0)
        val frame1 = buildFrame(scrollDelta)
        val p0 = savePng(tempDir, "f0.png", frame0)
        val p1 = savePng(tempDir, "f1.png", frame1)
        frame0.recycle()
        frame1.recycle()
        doc.recycle()

        val stitcher = ImageStitcher()
        val plan = stitcher.buildStitchPlan(
            framePaths = listOf(p0, p1),
            direction = StitchDirection.VERTICAL,
            overlapRange = 0.15f to 0.55f
        )

        assertEquals(1, plan.joins.size)
        val join = plan.joins.first()
        assertTrue("Expected non-duplicate, join=$join", !join.isDuplicate)
        assertTrue("Header crop should match ~60px, got ${join.headerCropPx}", join.headerCropPx in 56..64)
        assertTrue("Footer crop should match ~48px, got ${join.footerCropPx}", join.footerCropPx in 44..52)
        assertTrue("Overlap should be close to $overlap, got ${join.overlapPx}", kotlin.math.abs(join.overlapPx - overlap) <= 4)

        val rendered = stitcher.renderStitchPlan(plan, maxOutputLengthPx = 10000)
        assertTrue(rendered is StitchResult.Success)
        val outBmp = (rendered as StitchResult.Success).bitmap
        val expectedHeight = headerH + activeH + scrollDelta + footerH
        assertTrue("Output height ${outBmp.height} should be close to $expectedHeight", kotlin.math.abs(outBmp.height - expectedHeight) <= 8)
        outBmp.recycle()
        tempDir.deleteRecursively()
    }

    @Test
    fun `duplicate frames are skipped during stitching`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "dup_test_${System.currentTimeMillis()}").apply { mkdirs() }

        val bmp = makeScrollingDocumentBitmap(320, 480)
        val p0 = savePng(tempDir, "d0.png", bmp)
        val p1 = savePng(tempDir, "d1.png", bmp)
        bmp.recycle()

        val stitcher = ImageStitcher()
        val plan = stitcher.buildStitchPlan(
            framePaths = listOf(p0, p1),
            direction = StitchDirection.VERTICAL,
            overlapRange = 0.18f to 0.55f
        )

        assertEquals(1, plan.joins.size)
        assertTrue(plan.joins.first().isDuplicate)
        assertEquals(1, plan.activeFrameCount)

        val rendered = stitcher.renderStitchPlan(plan, maxOutputLengthPx = 10000)
        assertTrue(rendered is StitchResult.Success)
        val outBmp = (rendered as StitchResult.Success).bitmap
        assertEquals(480, outBmp.height)
        outBmp.recycle()
        tempDir.deleteRecursively()
    }

    @Test
    fun `horizontal stitch detects horizontal overlap`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "horiz_test_${System.currentTimeMillis()}").apply { mkdirs() }

        val wideDoc = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
        val c = Canvas(wideDoc)
        c.drawColor(Color.WHITE)
        val paint = Paint()
        for (x in 0 until 800 step 24) {
            paint.color = Color.HSVToColor(floatArrayOf(((x * 9) % 360).toFloat(), 0.8f, 0.8f))
            c.drawRect(x.toFloat(), 30f, (x + 16).toFloat(), 270f, paint)
        }

        val f0 = Bitmap.createBitmap(wideDoc, 0, 0, 480, 300)
        val f1 = Bitmap.createBitmap(wideDoc, 320, 0, 480, 300)
        val p0 = savePng(tempDir, "h0.png", f0)
        val p1 = savePng(tempDir, "h1.png", f1)
        f0.recycle()
        f1.recycle()
        wideDoc.recycle()

        val stitcher = ImageStitcher()
        val plan = stitcher.buildStitchPlan(
            framePaths = listOf(p0, p1),
            direction = StitchDirection.HORIZONTAL,
            overlapRange = 0.15f to 0.55f
        )

        assertEquals(1, plan.joins.size)
        val join = plan.joins.first()
        assertTrue("Expected ~160px overlap, got ${join.overlapPx}", kotlin.math.abs(join.overlapPx - 160) <= 4)

        val rendered = stitcher.renderStitchPlan(plan, maxOutputLengthPx = 10000)
        assertTrue(rendered is StitchResult.Success)
        val outBmp = (rendered as StitchResult.Success).bitmap
        assertTrue("Expected ~800px width, got ${outBmp.width}", kotlin.math.abs(outBmp.width - 800) <= 6)
        outBmp.recycle()
        tempDir.deleteRecursively()
    }
}
