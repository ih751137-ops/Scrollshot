package com.example.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

data class OcrResult(
    val fullText: String,
    val lineCount: Int,
    val wordCount: Int
)

class OnDeviceOcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractTextFromFile(
        filePath: String,
        onProgress: (Float) -> Unit = {}
    ): Result<OcrResult> = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeFile(filePath)
            ?: return@withContext Result.failure(IllegalStateException("Unable to decode image file."))

        return@withContext try {
            val result = extractTextFromBitmap(bitmap, onProgress)
            Result.success(result)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun extractTextFromSource(
        bitmapProvider: () -> Bitmap?,
        onProgress: (Float) -> Unit = {}
    ): Result<OcrResult> = withContext(Dispatchers.Default) {
        val bitmap = bitmapProvider()
            ?: return@withContext Result.failure(IllegalStateException("Unable to decode image file or MediaStore entry."))

        return@withContext try {
            val result = extractTextFromBitmap(bitmap, onProgress)
            Result.success(result)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun extractTextFromBitmap(
        bitmap: Bitmap,
        onProgress: (Float) -> Unit
    ): OcrResult {
        val chunkHeight = 1900
        val overlap = 180

        if (bitmap.height <= chunkHeight) {
            onProgress(0.5f)
            val text = processSingleBitmap(bitmap)
            onProgress(1f)
            val lines = text.lines().filter { it.isNotBlank() }
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            return OcrResult(text.trim(), lines.size, words.size)
        }

        val collectedLines = mutableListOf<String>()
        var y = 0
        var chunkIndex = 0
        val estimatedChunks = ((bitmap.height - overlap) / (chunkHeight - overlap).toFloat()).toInt().coerceAtLeast(1)

        while (y < bitmap.height) {
            val h = min(chunkHeight, bitmap.height - y)
            val slice = Bitmap.createBitmap(bitmap, 0, y, bitmap.width, h)
            val chunkText = try {
                processSingleBitmap(slice)
            } finally {
                slice.recycle()
            }

            val chunkLines = chunkText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            for (line in chunkLines) {
                // Deduplicate overlap zone lines
                val recentWindow = collectedLines.takeLast(8)
                if (line !in recentWindow) {
                    collectedLines.add(line)
                }
            }

            chunkIndex++
            onProgress((chunkIndex.toFloat() / estimatedChunks).coerceIn(0.1f, 0.98f))
            if (y + h >= bitmap.height) break
            y += (chunkHeight - overlap)
        }

        onProgress(1f)
        val merged = collectedLines.joinToString("\n")
        val words = merged.split(Regex("\\s+")).filter { it.isNotBlank() }
        return OcrResult(merged, collectedLines.size, words.size)
    }

    private suspend fun processSingleBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val input = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(input)
                .addOnSuccessListener { result: Text ->
                    if (cont.isActive) cont.resume(result.text)
                }
                .addOnFailureListener { exc ->
                    if (cont.isActive) cont.resumeWithException(exc)
                }
        }
}
