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
        var rejectBelowFare: Double = 50.0
        var acceptAboveFare: Double = 60.0

        val targetPackages = setOf(
            "com.rapido.rider",
            "com.ubercab.driver",
            "com.olacabs.partner",
            "com.theporter.partner"
        )
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isBotRunning || event == null) return

        val pkgName = event.packageName?.toString() ?: ""

        if (targetPackages.any { pkgName.contains(it, ignoreCase = true) } ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val rootNode = rootInActiveWindow ?: return
            processScreen(rootNode)
        }
    }

    private fun processScreen(root: AccessibilityNodeInfo) {
        val allNodes = ArrayList<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        // 1. Scan Fare strictly from all screen elements
        var detectedFare: Double? = null
        for (node in allNodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
            val fare = extractStrictFare(text)
            if (fare != null && fare > 0) {
                detectedFare = fare
                break
            }
        }

        // 2. Strict Decision Making
        if (detectedFare != null) {
            Log.d(TAG, "Screen Fare Detected: ₹$detectedFare | Accept Limit: ₹$acceptAboveFare | Reject Limit: ₹$rejectBelowFare")

            // 🔴 Agar Fare Minimum Reject limit se kam hai -> Reject
            if (detectedFare < rejectBelowFare) {
                Log.d(TAG, "Fare ₹$detectedFare is strictly LOWER than ₹$rejectBelowFare -> REJECTING")
                executeRejectFlow(allNodes)
                return
            }

            // 🟢 Agar Fare Accept limit se zyada ya barabar hai -> Accept
            if (detectedFare >= acceptAboveFare) {
                Log.d(TAG, "Fare ₹$detectedFare is EQUAL/HIGHER than ₹$acceptAboveFare -> ACCEPTING")
                executeAcceptFlow(allNodes)
                return
            }

            // 🟡 Agar Beech ka Fare hai -> Ignore (Do nothing)
            Log.d(TAG, "Fare ₹$detectedFare is between limits. No action taken.")
        } else {
            // STRICT SAFETY: Agar Fare detect NAHI hua, toh blind click bilkul NAHI karenge
            Log.d(TAG, "No valid fare symbol detected on screen. Skipping auto-accept for safety.")
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

    private fun extractStrictFare(text: String): Double? {
        if (text.isBlank()) return null

        // Ignore distance (km, m) and rating / timings
        if (text.contains("km", ignoreCase = true) || text.contains("mins", ignoreCase = true) || text.contains("min", ignoreCase = true)) {
            // Agar string me sirf distance hai toh ignore karein
            if (!text.contains("₹") && !text.contains("Rs", ignoreCase = true)) return null
        }

        // Match ₹120, ₹ 120, Rs. 120, 120 ₹, etc.
        val regexWithSymbol = Regex("""(?:₹|Rs\.?|INR)\s*(\d+(?:,\d+)*(?:\.\d+)?)""")
        val match = regexWithSymbol.find(text)
        if (match != null) {
            return match.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        // Alternative check: "120 ₹"
        val regexSuffix = Regex("""(\d+(?:,\d+)*(?:\.\d+)?)\s*(?:₹|Rs|INR)""")
        val matchSuffix = regexSuffix.find(text)
        if (matchSuffix != null) {
            return matchSuffix.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        return null
    }

    private fun executeRejectFlow(nodes: List<AccessibilityNodeInfo>) {
        for (node in nodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isRejectBtn = text.contains("reject") ||
                    text.contains("decline") ||
                    text.contains("dismiss") ||
                    text.contains("skip") ||
                    text.contains("cancel") ||
                    text.contains("✕") ||
                    text.contains("x") ||
                    viewId.contains("reject") ||
                    viewId.contains("cancel") ||
                    viewId.contains("close") ||
                    viewId.contains("dismiss")

            if (isRejectBtn) {
                Log.d(TAG, "Direct Reject button found, clicking: $text")
                triggerClick(node)
                return
            }
        }

        // Swipe down & Back action fallback to clear the popup
        swipeToDismiss()
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
        }, 150)
    }

    private fun executeAcceptFlow(nodes: List<AccessibilityNodeInfo>) {
        for (node in nodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isAcceptBtn = text.contains("accept") ||
                    text.contains("स्वीकार") ||
                    text.contains("take ride") ||
                    text.contains("book") ||
                    text.contains("order") ||
                    viewId.contains("accept") ||
                    viewId.contains("confirm")

            if (isAcceptBtn) {
                Log.d(TAG, "Accept button found, clicking: $text")
                triggerClick(node)
                break
            }
        }
    }

    private fun triggerClick(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (rect.centerX() <= 0 || rect.centerY() <= 0) {
            if (node.isClickable) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }

        val clickDelay = if (isFastTurboMode) 0L else if (isAntiBotEnabled) (120L..280L).random() else 40L

        handler.postDelayed({
            val path = Path().apply {
                moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 40))
                .build()

            dispatchGesture(gesture, null, null)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }, clickDelay)
    }

    private fun swipeToDismiss() {
        val startX = screenWidth * 0.5f
        val startY = screenHeight * 0.4f
        val endY = screenHeight * 0.85f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
        Log.e(TAG, "Service Interrupted")
    }
}
