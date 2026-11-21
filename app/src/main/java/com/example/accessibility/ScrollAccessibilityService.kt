package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.StitchDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ScrollAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isEnabled.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Privacy rule: ScrollShot never collects screen text or monitors unrelated app activity.
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isEnabled.value = false
    }

    suspend fun performAutoScroll(direction: StitchDirection): Boolean {
        val root = rootInActiveWindow
        // Never auto-scroll ScrollShot's own UI
        if (root != null && root.packageName?.toString() != packageName) {
            val scrollable = findLargestScrollableNode(root)
            if (scrollable != null) {
                val scrolled = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                if (scrolled) return true
            }
        }

        // Fallback to gesture swipe if node-based scrolling is unsupported (e.g., WebViews, custom canvases)
        return dispatchScrollGesture(direction)
    }

    private fun findLargestScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var bestNode: AccessibilityNodeInfo? = null
        var bestArea = 0L
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val rect = Rect()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable && node.isVisibleToUser) {
                node.getBoundsInScreen(rect)
                val area = rect.width().toLong() * rect.height().toLong()
                if (area > bestArea) {
                    bestArea = area
                    bestNode = node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return bestNode
    }

    private suspend fun dispatchScrollGesture(direction: StitchDirection): Boolean =
        suspendCancellableCoroutine { cont ->
            val metrics = resources.displayMetrics
            val w = metrics.widthPixels.toFloat()
            val h = metrics.heightPixels.toFloat()

            val path = Path().apply {
                if (direction == StitchDirection.VERTICAL) {
                    moveTo(w * 0.5f, h * 0.75f)
                    lineTo(w * 0.5f, h * 0.28f)
                } else {
                    moveTo(w * 0.80f, h * 0.5f)
                    lineTo(w * 0.22f, h * 0.5f)
                }
            }

            val stroke = GestureDescription.StrokeDescription(path, 0L, 380L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }

    companion object {
        @Volatile
        private var instance: ScrollAccessibilityService? = null

        private val _isEnabled = MutableStateFlow(false)
        val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

        fun current(): ScrollAccessibilityService? = instance
    }
}
