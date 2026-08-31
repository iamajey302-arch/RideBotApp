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
        Log.d(TAG, "Service Ready: $screenWidth x $screenHeight")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isBotRunning || event == null) return

        val allNodes = ArrayList<AccessibilityNodeInfo>()

        // 1. Gather all nodes from windows and active root
        val windowList = windows
        if (!windowList.isNullOrEmpty()) {
            for (window in windowList) {
                window.root?.let { collectAllNodes(it, allNodes) }
            }
        }

        if (allNodes.isEmpty()) {
            rootInActiveWindow?.let { collectAllNodes(it, allNodes) }
        }
        event.source?.let { collectAllNodes(it, allNodes) }

        if (allNodes.isEmpty()) return

        // 2. Identify Action Buttons
        val actionButton = findActionTarget(allNodes)

        if (actionButton != null) {
            val detectedFare = parseFareFromScreen(allNodes)
            Log.d(TAG, "Offer Detected! Fare: ₹$detectedFare | Button: ${actionButton.text}")

            if (detectedFare != null) {
                // 🟢 IN RANGE -> ACCEPT (Rapido: Accept, Uber: Confirm)
                if (detectedFare in minTargetFare..maxTargetFare) {
                    Log.d(TAG, "Fare ₹$detectedFare in target range. ACCEPTING...")
                    performClickAction(actionButton)
                    return
                }

                // 🔴 OUT OF RANGE -> REJECT (Rapido: ➖ minus, Uber: ✕ cross)
                if (detectedFare < minTargetFare || detectedFare > maxTargetFare) {
                    Log.d(TAG, "Fare ₹$detectedFare outside range. REJECTING...")
                    executeRejectAction(allNodes, actionButton)
                    return
                }
            } else {
                // Fallback: If fare is not parsed and user wants all rides (min <= 10)
                if (minTargetFare <= 10.0) {
                    performClickAction(actionButton)
                }
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

        // Primary: Match main fare symbols ignoring hourly rates (/hr, est) & premiums
        for (t in texts) {
            val clean = t.trim()
            if ((clean.contains("₹") || clean.contains("Rs", ignoreCase = true) || clean.contains("INR", ignoreCase = true)) &&
                !clean.contains("/hr", ignoreCase = true) &&
                !clean.contains("Premium", ignoreCase = true) &&
                !clean.contains("active hr", ignoreCase = true)
            ) {
                val regex = Regex("""(?:₹|Rs\.?|INR)\s*(\d+(?:,\d+)*(?:\.\d+)?)""")
                val match = regex.find(clean)
                if (match != null) {
                    val num = match.groupValues[1].replace(",", "").toDoubleOrNull()
                    if (num != null && num in 10.0..5000.0) return num
                }
            }
        }

        // Secondary: Plain numerical fare
        for (t in texts) {
            val clean = t.trim()
            if (!clean.contains("km", ignoreCase = true) && !clean.contains("min", ignoreCase = true)) {
                if (clean.matches(Regex("""^\d+(\.\d{1,2})?$"""))) {
                    val num = clean.toDoubleOrNull()
                    if (num != null && num in 15.0..3000.0) return num
                }
            }
        }

        return null
    }

    private fun findActionTarget(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (node in nodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()

            val isAcceptOrConfirm = text == "confirm" ||
                    text == "accept" ||
                    text.contains("confirm") ||
                    text.contains("accept") ||
                    text.contains("स्वीकार") ||
                    text.contains("take ride") ||
                    viewId.contains("confirm") ||
                    viewId.contains("accept") ||
                    viewId.contains("btn_accept")

            if (isAcceptOrConfirm) {
                var target = node
                while (target.parent != null && !target.isClickable) {
                    target = target.parent
                }
                return target
            }
        }
        return null
    }

    private fun executeRejectAction(nodes: List<AccessibilityNodeInfo>, actionBtn: AccessibilityNodeInfo) {
        // 1. Direct Node Check for Reject, Cross (✕), Minus (➖), Dismiss
        for (node in nodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()

            val isReject = text == "-" || text == "–" || text == "—" || text == "➖" ||
                    text == "✕" || text == "x" ||
                    text.contains("reject") ||
                    text.contains("decline") ||
                    text.contains("skip") ||
                    text.contains("dismiss") ||
                    text.contains("cancel") ||
                    viewId.contains("reject") ||
                    viewId.contains("cancel") ||
                    viewId.contains("close") ||
                    viewId.contains("cross") ||
                    viewId.contains("minus")

            if (isReject) {
                performClickAction(node)
                return
            }
        }

        // 2. Rapido Geometry Fallback: Tap the circular ➖ button located to the left of the Accept button
        val rect = Rect()
        actionBtn.getBoundsInScreen(rect)
        if (rect.left > 120) {
            val minusX = (rect.left - 80).toFloat()
            val minusY = rect.centerY().toFloat()
            Log.d(TAG, "Executing Rapido minus coordinate tap at ($minusX, $minusY)")
            clickDirectCoordinate(minusX, minusY, null)
            return
        }

        // 3. Uber Geometry Fallback: Tap top-right ✕ area of bottom sheet
        val crossX = screenWidth * 0.85f
        val crossY = screenHeight * 0.42f
        Log.d(TAG, "Executing Uber cross coordinate tap at ($crossX, $crossY)")
        clickDirectCoordinate(crossX, crossY, null)
    }

    private fun performClickAction(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val x = if (rect.centerX() > 0) rect.centerX().toFloat() else screenWidth * 0.5f
        val y = if (rect.centerY() > 0) rect.centerY().toFloat() else screenHeight * 0.90f

        clickDirectCoordinate(x, y, node)
    }

    private fun clickDirectCoordinate(x: Float, y: Float, node: AccessibilityNodeInfo?) {
        val delay = if (isFastTurboMode) 0L else if (isAntiBotEnabled) (30L..80L).random() else 10L

        handler.postDelayed({
            // Touch gesture tap at exact pixel coordinate
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 30))
                .build()
            dispatchGesture(gesture, null, null)

            // Direct node click trigger
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }, delay)
    }

    override fun onInterrupt() {
        Log.e(TAG, "Service Interrupted")
    }
}
