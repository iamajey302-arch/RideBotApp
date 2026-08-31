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

        val pkgName = event.packageName?.toString() ?: return

        if (targetPackages.any { pkgName.contains(it, ignoreCase = true) } ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val rootNode = rootInActiveWindow ?: return
            processScreen(rootNode)
        }
    }

    private fun processScreen(root: AccessibilityNodeInfo) {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        var detectedFare: Double? = null
        for (node in allNodes) {
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            val fare = extractFare(text)
            if (fare != null && fare > 0) {
                detectedFare = fare
                break
            }
        }

        // --- FARE BASED DECISION ---
        if (detectedFare != null) {
            Log.d(TAG, "Detected Fare: ₹$detectedFare")

            // 🔴 REJECT CONDITION: Agar fare Min Fare se kam hai
            if (detectedFare < rejectBelowFare) {
                Log.d(TAG, "Fare ₹$detectedFare is BELOW threshold ₹$rejectBelowFare -> REJECTING RIDE")
                performRejectAction(allNodes)
                return
            }

            // 🟢 ACCEPT CONDITION: Agar fare Max/Accept Fare se barabar ya zyada hai
            if (detectedFare >= acceptAboveFare) {
                Log.d(TAG, "Fare ₹$detectedFare is ABOVE threshold ₹$acceptAboveFare -> ACCEPTING RIDE")
                performAcceptAction(allNodes)
                return
            }
        } else {
            // Agar fare detect nahi hua lekin auto mode active hai toh check karein
            performAcceptAction(allNodes)
        }
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo?, list: mutableListOf<AccessibilityNodeInfo> = mutableListOf()) {
        if (node == null) return
        list.add(node)
        for (i in 0 until node.childCount) {
            collectAllNodes(node.getChild(i), list)
        }
    }

    private fun extractFare(text: String): Double? {
        val regex = Regex("""[₹RsRS\.]*\s*(\d+[\d,]*)""")
        val match = regex.find(text) ?: return null
        return match.groupValues[1].replace(",", "").toDoubleOrNull()
    }

    private fun performRejectAction(nodes: List<AccessibilityNodeInfo>) {
        var clicked = false

        // 1. Search for Reject / Cross / Dismiss keywords or view IDs
        for (node in nodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isRejectBtn = text.contains("reject") ||
                              text.contains("decline") ||
                              text.contains("cancel") ||
                              text.contains("dismiss") ||
                              text.contains("skip") ||
                              text.contains("✕") ||
                              text.contains("x") ||
                              viewId.contains("reject") ||
                              viewId.contains("close") ||
                              viewId.contains("cancel") ||
                              viewId.contains("cross")

            if (isRejectBtn) {
                Log.d(TAG, "Reject button matched: $text | ID: $viewId")
                triggerClick(node)
                clicked = true
                break
            }
        }

        // 2. Fallback: Tap near the top-right cross icon of ride popup if not found
        if (!clicked) {
            Log.d(TAG, "Tapping fallback top-right reject coordinate")
            clickCoordinate((screenWidth * 0.88f), (screenHeight * 0.35f))
        }
    }

    private fun performAcceptAction(nodes: List<AccessibilityNodeInfo>) {
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
                Log.d(TAG, "Accept button matched: $text")
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

        clickCoordinate(rect.centerX().toFloat(), rect.centerY().toFloat(), node)
    }

    private fun clickCoordinate(x: Float, y: Float, fallbackNode: AccessibilityNodeInfo? = null) {
        val clickDelay = if (isFastTurboMode) 0L else if (isAntiBotEnabled) (150L..350L).random() else 50L

        handler.postDelayed({
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()

            dispatchGesture(gesture, null, null)
            fallbackNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }, clickDelay)
    }

    override fun onInterrupt() {
        Log.e(TAG, "Service Interrupted")
    }
}
