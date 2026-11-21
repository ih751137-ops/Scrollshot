package com.example.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.example.data.local.ScreenshotEntity
import com.example.data.model.PdfPageSize
import com.example.data.storage.MediaStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

data class PdfExportResult(
    val file: File,
    val pageCount: Int,
    val sizeBytes: Long
)

class PdfExporter(private val storage: MediaStorageManager) {

    suspend fun exportScreenshotsToPdf(
        screenshots: List<ScreenshotEntity>,
        pageSize: PdfPageSize,
        jpegQuality: Int,
        fitLongToSinglePage: Boolean,
        outputTitle: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<PdfExportResult> = withContext(Dispatchers.IO) {
        if (screenshots.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No screenshots selected."))
        }

        val pdf = PdfDocument()
        var totalPages = 0
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        try {
            screenshots.forEachIndexed { itemIdx, shot ->
                val rawBmp = storage.decodeBitmapSafely(shot.filePath, shot.mediaStoreUri) ?: return@forEachIndexed
                val bmp = recompressForPdfIfNeeded(rawBmp, jpegQuality)
                if (bmp !== rawBmp) rawBmp.recycle()

                if (pageSize == PdfPageSize.ORIGINAL_FIT || (fitLongToSinglePage && shot.isLongScreenshot)) {
                    // Preserve original aspect ratio on a page sized cleanly to the image
                    val pageW = if (pageSize == PdfPageSize.ORIGINAL_FIT) {
                        bmp.width.coerceIn(300, 2400)
                    } else {
                        pageSize.widthPt
                    }
                    val scale = pageW.toFloat() / bmp.width.toFloat()
                    val pageH = (bmp.height * scale).toInt().coerceAtLeast(100)

                    totalPages++
                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, totalPages).create()
                    val page = pdf.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(
                        bmp,
                        Rect(0, 0, bmp.width, bmp.height),
                        Rect(0, 0, pageW, pageH),
                        paint
                    )
                    pdf.finishPage(page)
                } else {
                    // Paginate tall screenshots across standard A4/Letter pages at crisp readable width
                    val margin = 20
                    val contentW = pageSize.widthPt - margin * 2
                    val contentH = pageSize.heightPt - margin * 2
                    val scale = contentW.toFloat() / bmp.width.toFloat()
                    val sliceSrcHeight = (contentH / scale).toInt().coerceAtLeast(100)
                    val slices = ceil(bmp.height.toDouble() / sliceSrcHeight).toInt().coerceAtLeast(1)

                    for (s in 0 until slices) {
                        val srcTop = s * sliceSrcHeight
                        val srcBottom = min(bmp.height, srcTop + sliceSrcHeight)
                        val sliceH = srcBottom - srcTop
                        val dstH = (sliceH * scale).toInt().coerceAtLeast(1)

                        totalPages++
                        val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, totalPages).create()
                        val page = pdf.startPage(pageInfo)
                        page.canvas.drawColor(Color.WHITE)
                        page.canvas.drawBitmap(
                            bmp,
                            Rect(0, srcTop, bmp.width, srcBottom),
                            Rect(margin, margin, margin + contentW, margin + dstH),
                            paint
                        )
                        pdf.finishPage(page)
                    }
                }

                bmp.recycle()
                onProgress((itemIdx + 1).toFloat() / screenshots.size)
            }

            if (totalPages == 0) {
                return@withContext Result.failure(IllegalStateException("Could not decode selected screenshots."))
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${outputTitle?.ifBlank { null } ?: "ScrollShot_$stamp"}.pdf"
            val outFile = storage.createExportFile(fileName)
            FileOutputStream(outFile).use { stream ->
                pdf.writeTo(stream)
            }
            storage.publishDocumentToMediaStore(outFile, fileName, "application/pdf")

            Result.success(
                PdfExportResult(
                    file = outFile,
                    pageCount = totalPages,
                    sizeBytes = outFile.length()
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            pdf.close()
        }
    }

    private fun recompressForPdfIfNeeded(bitmap: Bitmap, jpegQuality: Int): Bitmap {
        if (jpegQuality >= 98) return bitmap
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(55, 98), baos)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bitmap
    }
}
