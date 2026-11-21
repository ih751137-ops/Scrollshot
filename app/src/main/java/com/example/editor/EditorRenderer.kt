package com.example.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object EditorRenderer {

    suspend fun render(
        source: Bitmap,
        operations: List<EditorOperation>
    ): Bitmap = withContext(Dispatchers.Default) {
        var current = source.copy(Bitmap.Config.ARGB_8888, true)

        for (op in operations) {
            when (op) {
                is EditorOperation.Crop -> {
                    val r = op.rect.normalized()
                    val x = (r.left * current.width).toInt().coerceIn(0, current.width - 1)
                    val y = (r.top * current.height).toInt().coerceIn(0, current.height - 1)
                    val w = (r.width * current.width).toInt().coerceIn(1, current.width - x)
                    val h = (r.height * current.height).toInt().coerceIn(1, current.height - y)
                    val cropped = Bitmap.createBitmap(current, x, y, w, h)
                    current.recycle()
                    current = cropped.copy(Bitmap.Config.ARGB_8888, true)
                    cropped.recycle()
                }
                is EditorOperation.Rotate90 -> {
                    val matrix = Matrix().apply { postRotate(if (op.clockwise) 90f else -90f) }
                    val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
                    current.recycle()
                    current = rotated.copy(Bitmap.Config.ARGB_8888, true)
                    rotated.recycle()
                }
                is EditorOperation.Flip -> {
                    val matrix = Matrix().apply {
                        if (op.horizontal) postScale(-1f, 1f) else postScale(1f, -1f)
                    }
                    val flipped = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
                    current.recycle()
                    current = flipped.copy(Bitmap.Config.ARGB_8888, true)
                    flipped.recycle()
                }
                is EditorOperation.Resize -> {
                    val targetW = op.width.coerceIn(32, 8000)
                    val targetH = op.height.coerceIn(32, 30000)
                    val resized = Bitmap.createScaledBitmap(current, targetW, targetH, true)
                    current.recycle()
                    current = resized.copy(Bitmap.Config.ARGB_8888, true)
                    resized.recycle()
                }
                is EditorOperation.PrivacyMask -> {
                    applyPrivacyMask(current, op)
                }
                is EditorOperation.Stroke -> drawStroke(current, op)
                is EditorOperation.Arrow -> drawArrow(current, op)
                is EditorOperation.Shape -> drawShape(current, op)
                is EditorOperation.TextLabel -> drawText(current, op)
                is EditorOperation.NumberBadge -> drawNumberBadge(current, op)
            }
        }
        current
    }

    private fun applyPrivacyMask(bitmap: Bitmap, op: EditorOperation.PrivacyMask) {
        val r = op.rect.normalized()
        val x = (r.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (r.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val w = (r.width * bitmap.width).toInt().coerceIn(2, bitmap.width - x)
        val h = (r.height * bitmap.height).toInt().coerceIn(2, bitmap.height - y)

        val region = Bitmap.createBitmap(bitmap, x, y, w, h)
        val processed = if (op.isPixelate) {
            pixelateBitmap(region, max(8, (min(bitmap.width, bitmap.height) * 0.018f).toInt()))
        } else {
            blurBitmap(region, max(10, op.blockSize))
        }
        region.recycle()

        val canvas = Canvas(bitmap)
        canvas.drawBitmap(processed, x.toFloat(), y.toFloat(), null)
        processed.recycle()
    }

    private fun pixelateBitmap(src: Bitmap, pixelSize: Int): Bitmap {
        val downW = max(1, src.width / pixelSize)
        val downH = max(1, src.height / pixelSize)
        val tiny = Bitmap.createScaledBitmap(src, downW, downH, false)
        val mosaic = Bitmap.createScaledBitmap(tiny, src.width, src.height, false)
        tiny.recycle()
        return mosaic
    }

    private fun blurBitmap(src: Bitmap, radius: Int): Bitmap {
        // Multi-pass downscale + bilinear upscale produces fast, clean privacy blur on all API levels
        val factor = (radius / 3f).coerceIn(4f, 16f)
        val downW = max(2, (src.width / factor).toInt())
        val downH = max(2, (src.height / factor).toInt())
        val pass1 = Bitmap.createScaledBitmap(src, downW, downH, true)
        val pass2 = Bitmap.createScaledBitmap(pass1, max(2, downW / 2), max(2, downH / 2), true)
        pass1.recycle()
        val blurred = Bitmap.createScaledBitmap(pass2, src.width, src.height, true)
        pass2.recycle()
        return blurred
    }

    private fun drawStroke(bitmap: Bitmap, op: EditorOperation.Stroke) {
        if (op.points.size < 2) return
        val canvas = Canvas(bitmap)
        val baseDim = min(bitmap.width, bitmap.height).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (op.isHighlighter) {
                Color.argb(115, Color.red(op.colorArgb), Color.green(op.colorArgb), Color.blue(op.colorArgb))
            } else {
                op.colorArgb
            }
            style = Paint.Style.STROKE
            strokeCap = if (op.isHighlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = (op.strokeWidthNorm * baseDim).coerceAtLeast(3f)
        }
        val path = Path()
        path.moveTo(op.points.first().x * bitmap.width, op.points.first().y * bitmap.height)
        for (i in 1 until op.points.size) {
            val pt = op.points[i]
            path.lineTo(pt.x * bitmap.width, pt.y * bitmap.height)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawArrow(bitmap: Bitmap, op: EditorOperation.Arrow) {
        val canvas = Canvas(bitmap)
        val baseDim = min(bitmap.width, bitmap.height).toFloat()
        val strokeW = (op.strokeWidthNorm * baseDim).coerceAtLeast(4f)
        val sx = op.start.x * bitmap.width
        val sy = op.start.y * bitmap.height
        val ex = op.end.x * bitmap.width
        val ey = op.end.y * bitmap.height

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = op.colorArgb
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(sx, sy, ex, ey, paint)

        val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
        val headLen = strokeW * 4.2f
        val wingAngle = Math.toRadians(28.0)

        val x1 = (ex - headLen * cos(angle - wingAngle)).toFloat()
        val y1 = (ey - headLen * sin(angle - wingAngle)).toFloat()
        val x2 = (ex - headLen * cos(angle + wingAngle)).toFloat()
        val y2 = (ey - headLen * sin(angle + wingAngle)).toFloat()

        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = op.colorArgb
            style = Paint.Style.FILL
        }
        val headPath = Path().apply {
            moveTo(ex, ey)
            lineTo(x1, y1)
            lineTo(x2, y2)
            close()
        }
        canvas.drawPath(headPath, headPaint)
    }

    private fun drawShape(bitmap: Bitmap, op: EditorOperation.Shape) {
        val canvas = Canvas(bitmap)
        val baseDim = min(bitmap.width, bitmap.height).toFloat()
        val l = min(op.start.x, op.end.x) * bitmap.width
        val t = min(op.start.y, op.end.y) * bitmap.height
        val r = max(op.start.x, op.end.x) * bitmap.width
        val b = max(op.start.y, op.end.y) * bitmap.height

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (op.filled) {
                Color.argb(90, Color.red(op.colorArgb), Color.green(op.colorArgb), Color.blue(op.colorArgb))
            } else {
                op.colorArgb
            }
            style = if (op.filled) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = (op.strokeWidthNorm * baseDim).coerceAtLeast(4f)
        }
        val rect = RectF(l, t, r, b)
        when (op.kind) {
            ShapeKind.RECTANGLE -> canvas.drawRoundRect(rect, 12f, 12f, paint)
            ShapeKind.OVAL -> canvas.drawOval(rect, paint)
        }
    }

    private fun drawText(bitmap: Bitmap, op: EditorOperation.TextLabel) {
        val canvas = Canvas(bitmap)
        val baseDim = min(bitmap.width, bitmap.height).toFloat()
        val textSizePx = (op.sizeNorm * baseDim).coerceAtLeast(28f)
        val x = op.position.x * bitmap.width
        val y = op.position.y * bitmap.height

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = op.colorArgb
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        if (op.backgroundPill) {
            val bounds = Rect()
            textPaint.getTextBounds(op.text, 0, op.text.length, bounds)
            val padX = textSizePx * 0.45f
            val padY = textSizePx * 0.3f
            val bgRect = RectF(
                x - padX,
                y + bounds.top - padY,
                x + bounds.width() + padX,
                y + bounds.bottom + padY
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (op.colorArgb == Color.WHITE) Color.argb(210, 15, 23, 42) else Color.argb(225, 255, 255, 255)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(bgRect, textSizePx * 0.4f, textSizePx * 0.4f, bgPaint)
        }
        canvas.drawText(op.text, x, y, textPaint)
    }

    private fun drawNumberBadge(bitmap: Bitmap, op: EditorOperation.NumberBadge) {
        val canvas = Canvas(bitmap)
        val baseDim = min(bitmap.width, bitmap.height).toFloat()
        val radius = (baseDim * 0.036f).coerceIn(22f, 52f)
        val cx = op.position.x * bitmap.width
        val cy = op.position.y * bitmap.height

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = op.colorArgb
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.14f
        }
        canvas.drawCircle(cx, cy, radius, circlePaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        val label = op.number.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (op.colorArgb == Color.WHITE) Color.BLACK else Color.WHITE
            textSize = radius * 1.15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val baseline = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(label, cx, baseline, textPaint)
    }
}
