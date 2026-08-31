package com.example.ridebot.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class RideAccessibilityService : AccessibilityService() {

    companion object {
        var isBotRunning = true
        var isFastTurboMode = false
        var isAntiBotEnabled = true
        var minTargetFare: Double = 1.0
        var maxTargetFare: Double = 2000.0
    }

    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "RideBot"
    private var screenWidth = 1080
    private var screenHeight = 2400

    override fun onServiceConnected() {
        super.onServiceConnected()
        val metrics: DisplayMetrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        Log.d(TAG, "Service Connected. Screen Size: $screenWidth x $screenHeight")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isBotRunning || event == null) return

        val allNodes = ArrayList<AccessibilityNodeInfo>()

        // 1. Scan across all interactive windows
        val windowList = windows
        if (!windowList.isNullOrEmpty()) {
            for (window in windowList) {
                window.root?.let { collectAllNodes(it, allNodes) }
            }
        }

        // Fallback root
        if (allNodes.isEmpty()) {
            rootInActiveWindow?.let { collectAllNodes(it, allNodes) }
        }
        event.source?.let { collectAllNodes(it, allNodes) }

        if (allNodes.isEmpty()) return

        // 2. Scan Screen Text & Fare
        val detectedFare = parseFareFromScreen(allNodes)

        // 3. Scan Accept Button
        val acceptBtn = findAcceptTarget(allNodes)

        if (acceptBtn != null) {
            Log.d(TAG, "Ride Detected! Screen Fare: ₹$detectedFare | Target: ₹$minTargetFare - ₹$maxTargetFare")

            if (detectedFare != null) {
                if (detectedFare in minTargetFare..maxTargetFare) {
                    Log.d(TAG, "Fare within range. Triggering ACCEPT!")
                    performClickAction(acceptBtn)
                    return
                } else {
                    Log.d(TAG, "Fare out of range. Triggering REJECT!")
                    performRejectAction(allNodes)
                    return
                }
            } else {
                // Agar Fare screen par parse na ho sake, tab bhi active bot accept karega
                Log.d(TAG, "Fare text generic. Executing Accept.")
                performClickAction(acceptBtn)
            }
        }
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo?, list: ArrayList<AccessibilityNodeInfo>) {
        if (node == null) return
        list.add(node)
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllNodes(child, list)
            }
        }
    }

    private fun parseFareFromScreen(nodes: List<AccessibilityNodeInfo>): Double? {
        val texts = nodes.mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }
            .filter { it.isNotBlank() }

        // Method A: Check single string with currency
        for (t in texts) {
            val clean = t.trim()
            if (clean.contains("₹") || clean.contains("Rs", ignoreCase = true) || clean.contains("INR", ignoreCase = true)) {
                val num = clean.replace(Regex("""[^\d.]"""), "").toDoubleOrNull()
                if (num != null && num in 10.0..5000.0) return num
            }
        }

        // Method B: Check adjacent nodes where "₹" is in one node and number in next
        for (i in texts.indices) {
            val t = texts[i].trim()
            if (t == "₹" || t.equals("Rs", ignoreCase = true) || t.equals("Rs.", ignoreCase = true)) {
                if (i + 1 < texts.size) {
                    val nextNum = texts[i + 1].trim().replace(Regex("""[^\d.]"""), "").toDoubleOrNull()
                    if (nextNum != null && nextNum in 10.0..5000.0) return nextNum
                }
            }
        }

        // Method C: Any prominent standalone number between 20 and 3000
        for (t in texts) {
            val clean = t.trim()
            if (!clean.contains("km", ignoreCase = true) && !clean.contains("min", ignoreCase = true) && !clean.contains("%")) {
                if (clean.matches(Regex("""^\d+(\.\d{1,2})?$"""))) {
                    val num = clean.toDoubleOrNull()
                    if (num != null && num in 20.0..3000.0) return num
                }
            }
        }

        return null
    }

    private fun findAcceptTarget(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (node in nodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()

            val isAccept = text.contains("accept") ||
                    text.contains("स्वीकार") ||
                    text.contains("take ride") ||
                    text.contains("order le") ||
                    text.contains("book") ||
                    text.contains("tap to accept") ||
                    viewId.contains("accept") ||
                    viewId.contains("confirm") ||
                    viewId.contains("btn_accept") ||
                    viewId.contains("slide")

            if (isAccept) {
                // Find clickable parent if child is non-clickable
                var target = node
                while (target.parent != null && !target.isClickable) {
                    target = target.parent
                }
                return target
            }
        }
        return null
    }

    private fun performRejectAction(nodes: List<AccessibilityNodeInfo>) {
        for (node in nodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()

            val isReject = text.contains("reject") ||
                    text.contains("decline") ||
                    text.contains("dismiss") ||
                    text.contains("skip") ||
                    text.contains("cancel") ||
                    text.contains("✕") ||
                    text.contains("x") ||
                    viewId.contains("reject") ||
                    viewId.contains("close") ||
                    viewId.contains("cross")

            if (isReject) {
                performClickAction(node)
                return
            }
        }

        // Dismiss Fallback
        val startX = screenWidth * 0.5f
        val startY = screenHeight * 0.45f
        val endY = screenHeight * 0.85f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 120))
            .build()
        dispatchGesture(gesture, null, null)

        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
        }, 120)
    }

    private fun performClickAction(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val x = if (rect.centerX() > 0) rect.centerX().toFloat() else screenWidth * 0.5f
        val y = if (rect.centerY() > 0) rect.centerY().toFloat() else screenHeight * 0.85f

        val delay = if (isFastTurboMode) 0L else if (isAntiBotEnabled) (60L..150L).random() else 20L

        handler.postDelayed({
            // 1. Hardware-level physical touch coordinate tap
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 40))
                .build()
            dispatchGesture(gesture, null, null)

            // 2. Direct accessibility node click
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }, delay)
    }

    override fun onInterrupt() {
        Log.e(TAG, "Service Interrupted")
    }
}
