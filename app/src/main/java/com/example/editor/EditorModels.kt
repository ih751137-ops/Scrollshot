package com.example.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class EditorTool {
    VIEW,
    CROP,
    ROTATE,
    RESIZE,
    PEN,
    HIGHLIGHTER,
    ARROW,
    SHAPE,
    TEXT,
    NUMBER,
    BLUR,
    PIXELATE
}

enum class ShapeKind {
    RECTANGLE,
    OVAL
}

enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    NINE_SIXTEEN("9:16", 9f / 16f)
}

data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun normalized(): NormRect = NormRect(
        left = minOf(left, right).coerceIn(0f, 1f),
        top = minOf(top, bottom).coerceIn(0f, 1f),
        right = maxOf(left, right).coerceIn(0f, 1f),
        bottom = maxOf(top, bottom).coerceIn(0f, 1f)
    )

    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

sealed interface EditorOperation {
    data class Crop(val rect: NormRect) : EditorOperation
    data class Rotate90(val clockwise: Boolean) : EditorOperation
    data class Flip(val horizontal: Boolean) : EditorOperation
    data class Resize(val width: Int, val height: Int) : EditorOperation

    data class Stroke(
        val points: List<Offset>,
        val colorArgb: Int,
        val strokeWidthNorm: Float,
        val isHighlighter: Boolean
    ) : EditorOperation

    data class Arrow(
        val start: Offset,
        val end: Offset,
        val colorArgb: Int,
        val strokeWidthNorm: Float
    ) : EditorOperation

    data class Shape(
        val start: Offset,
        val end: Offset,
        val kind: ShapeKind,
        val colorArgb: Int,
        val strokeWidthNorm: Float,
        val filled: Boolean
    ) : EditorOperation

    data class TextLabel(
        val position: Offset,
        val text: String,
        val colorArgb: Int,
        val sizeNorm: Float,
        val backgroundPill: Boolean
    ) : EditorOperation

    data class NumberBadge(
        val position: Offset,
        val number: Int,
        val colorArgb: Int
    ) : EditorOperation

    data class PrivacyMask(
        val rect: NormRect,
        val isPixelate: Boolean,
        val blockSize: Int
    ) : EditorOperation
}

val EditorColorPalette = listOf(
    Color(0xFFEF4444).toArgb(), // Red
    Color(0xFFF59E0B).toArgb(), // Amber
    Color(0xFF10B981).toArgb(), // Emerald
    Color(0xFF00B4D8).toArgb(), // Cyan
    Color(0xFF3B82F6).toArgb(), // Blue
    Color(0xFF8B5CF6).toArgb(), // Violet
    Color(0xFFFFFFFF).toArgb(), // White
    Color(0xFF0F172A).toArgb()  // Obsidian
)
