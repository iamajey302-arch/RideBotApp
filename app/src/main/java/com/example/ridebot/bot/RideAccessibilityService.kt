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
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.*

enum class RideStatus {
    AUTO_ACCEPTED,
    MANUAL_ACCEPTED,
    REJECTED,
    MISSED
}

data class RideLogItem(
    val id: String = UUID.randomUUID().toString(),
    val appName: String,
    val fare: Double,
    val pickupDist: String = "",
    val dropDist: String = "",
    val pickupLocation: String = "",
    val dropLocation: String = "",
    val status: RideStatus,
    val timestamp: String,
    val note: String = ""
)

class RideAccessibilityService : AccessibilityService() {

    companion object {
        var isBotRunning = true
        var isFastTurboMode = true
        var isAntiBotEnabled = false
        var minTargetFare: Double = 1.0
        var maxTargetFare: Double = 2000.0
        var maxPickupDistanceKm: Double = 5.0
        var rejectBikeBoost: Boolean = true

        val rideLogs = mutableStateListOf<RideLogItem>()

        fun addLog(
            appName: String,
            fare: Double,
            pickupDist: String,
            dropDist: String,
            pickupLoc: String,
            dropLoc: String,
            status: RideStatus,
            note: String = ""
        ) {
            val time = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
            rideLogs.add(
                0,
                RideLogItem(
                    appName = appName,
                    fare = fare,
                    pickupDist = pickupDist,
                    dropDist = dropDist,
                    pickupLocation = pickupLoc,
                    dropLocation = dropLoc,
                    status = status,
                    timestamp = time,
                    note = note
                )
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "RideBot"
    private var screenWidth = 1080
    private var screenHeight = 2400
    private var lastActionTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val metrics: DisplayMetrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTimestamp < 1500) return

        val pkgName = event?.packageName?.toString() ?: ""
        val appDetected = when {
            pkgName.contains("porter", ignoreCase = true) -> "Porter"
            pkgName.contains("rapido", ignoreCase = true) -> "Rapido"
            pkgName.contains("uber", ignoreCase = true) -> "Uber Driver"
            pkgName.contains("ola", ignoreCase = true) -> "Ola Partner"
            else -> "Ride App"
        }

        val allNodes = ArrayList<AccessibilityNodeInfo>()
        val windowList = windows
        if (!windowList.isNullOrEmpty()) {
            for (window in windowList) {
                window.root?.let { collectAllNodes(it, allNodes) }
            }
        }
        if (allNodes.isEmpty()) {
            rootInActiveWindow?.let { collectAllNodes(it, allNodes) }
        }
        event?.source?.let { collectAllNodes(it, allNodes) }

        if (allNodes.isEmpty()) return

        val actionButton = findActionTarget(allNodes) ?: return
        val detectedFare = parseFareFromOfferCard(allNodes)
        val rideDetails = parseRideDetails(allNodes)
        val pickupKmValue = extractKmNumber(rideDetails.pickupDist)

        val isBikeBoost = isBikeBoostOffer(allNodes)

        if (isBotRunning) {
            val finalFare = detectedFare ?: 0.0

            // 1. Rapido Bike Boost Auto-Reject
            if (rejectBikeBoost && isBikeBoost && appDetected.contains("Rapido", ignoreCase = true)) {
                lastActionTimestamp = currentTime
                executeRejectAction(allNodes, actionButton)
                addLog(
                    appName = appDetected,
                    fare = finalFare,
                    pickupDist = rideDetails.pickupDist,
                    dropDist = rideDetails.dropDist,
                    pickupLoc = rideDetails.pickupLoc,
                    dropLoc = rideDetails.dropLoc,
                    status = RideStatus.REJECTED,
                    note = "Auto-rejected: Bike Boost ride blocked"
                )
                return
            }

            // 2. Acceptance Criteria Check
            val isFareInRange = (finalFare in minTargetFare..maxTargetFare) || (finalFare == 0.0 && minTargetFare <= 10.0)
            val isPickupInRange = (pickupKmValue == null) || (pickupKmValue <= maxPickupDistanceKm)

            if (isFareInRange && isPickupInRange) {
                lastActionTimestamp = currentTime
                // 🟢 0ms Instant Swipe & Click Trigger
                performAcceptClick(actionButton)
                addLog(
                    appName = appDetected,
                    fare = finalFare,
                    pickupDist = rideDetails.pickupDist,
                    dropDist = rideDetails.dropDist,
                    pickupLoc = rideDetails.pickupLoc,
                    dropLoc = rideDetails.dropLoc,
                    status = RideStatus.AUTO_ACCEPTED,
                    note = "Auto accepted instantly (0ms)"
                )
            } else {
                lastActionTimestamp = currentTime
                val rejectReason = if (!isFareInRange) "Fare ₹$finalFare out of range" else "Pickup ${rideDetails.pickupDist} > $maxPickupDistanceKm km limit"
                executeRejectAction(allNodes, actionButton)
                addLog(
                    appName = appDetected,
                    fare = finalFare,
                    pickupDist = rideDetails.pickupDist,
                    dropDist = rideDetails.dropDist,
                    pickupLoc = rideDetails.pickupLoc,
                    dropLoc = rideDetails.dropLoc,
                    status = RideStatus.REJECTED,
                    note = "Rejected: $rejectReason"
                )
            }
        }
    }

    private fun isBikeBoostOffer(nodes: List<AccessibilityNodeInfo>): Boolean {
        val texts = nodes.mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }
            .map { it.trim().lowercase() }
        return texts.any { it.contains("boost") || it.contains("bike boost") }
    }

    private fun extractKmNumber(distStr: String): Double? {
        if (distStr.isBlank()) return null
        val regex = Regex("""(\d+(?:\.\d+)?)\s*km""", RegexOption.IGNORE_CASE)
        val match = regex.find(distStr)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun parseFareFromOfferCard(nodes: List<AccessibilityNodeInfo>): Double? {
        val texts = nodes.mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val fareCandidates = mutableListOf<Double>()

        for (t in texts) {
            if (t.contains("/hr", ignoreCase = true) ||
                t.contains("active hr", ignoreCase = true) ||
                t.contains("Includes", ignoreCase = true) ||
                t.contains("extra", ignoreCase = true) ||
                t.contains("Premium", ignoreCase = true) ||
                t.contains("today", ignoreCase = true) ||
                t.contains("balance", ignoreCase = true) ||
                t.contains("Ledger", ignoreCase = true) ||
                t.contains("Recharge", ignoreCase = true)
            ) {
                continue
            }

            if (t.contains("₹") || t.contains("Rs", ignoreCase = true) || t.contains("INR", ignoreCase = true)) {
                val regex = Regex("""(?:₹|Rs\.?|INR)\s*(\d+(?:,\d+)*(?:\.\d+)?)""")
                val match = regex.find(t)
                if (match != null) {
                    val num = match.groupValues[1].replace(",", "").toDoubleOrNull()
                    if (num != null && num in 15.0..5000.0) {
                        fareCandidates.add(num)
                    }
                }
            }
        }

        if (fareCandidates.isNotEmpty()) {
            return fareCandidates[0]
        }

        for (t in texts) {
            if (!t.contains("km", ignoreCase = true) && !t.contains("min", ignoreCase = true) && !t.contains("★") && !t.contains("s")) {
                if (t.matches(Regex("""^\d+(\.\d{1,2})?$"""))) {
                    val num = t.toDoubleOrNull()
                    if (num != null && num in 20.0..3000.0) return num
                }
            }
        }

        return null
    }

    private data class ParsedDetails(
        val pickupDist: String = "",
        val dropDist: String = "",
        val pickupLoc: String = "",
        val dropLoc: String = ""
    )

    private fun parseRideDetails(nodes: List<AccessibilityNodeInfo>): ParsedDetails {
        val rawTexts = nodes.mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val distances = mutableListOf<String>()
        val addressList = mutableListOf<String>()

        for (t in rawTexts) {
            val distRegex = Regex("""(\d+(\.\d+)?\s*km)""", RegexOption.IGNORE_CASE)
            val match = distRegex.find(t)
            if (match != null) {
                distances.add(match.groupValues[1])
            }

            if ((t.contains("Delhi", ignoreCase = true) ||
                 t.contains("Nagar", ignoreCase = true) ||
                 t.contains("Bagh", ignoreCase = true) ||
                 t.contains("Chowk", ignoreCase = true) ||
                 t.contains("Dairy", ignoreCase = true) ||
                 t.contains("Road", ignoreCase = true) ||
                 t.contains("Sector", ignoreCase = true) ||
                 t.contains("Gali", ignoreCase = true) ||
                 t.contains("Khurd", ignoreCase = true) ||
                 t.contains("Puna", ignoreCase = true) ||
                 t.contains("Block", ignoreCase = true)) &&
                !t.contains("₹") && !t.contains("min", ignoreCase = true)
            ) {
                if (!addressList.contains(t)) {
                    addressList.add(t)
                }
            }
        }

        val pickupD = if (distances.isNotEmpty()) distances[0] else ""
        val dropD = if (distances.size > 1) distances[1] else if (distances.isNotEmpty()) distances[0] else ""

        val pickupL = if (addressList.isNotEmpty()) addressList[0] else "Pickup Location"
        val dropL = if (addressList.size > 1) addressList[1] else if (addressList.isNotEmpty()) addressList[0] else "Drop Location"

        return ParsedDetails(pickupDist = pickupD, dropDist = dropD, pickupLoc = pickupL, dropLoc = dropL)
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

    private fun findActionTarget(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (node in nodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()

            val isAcceptOrConfirm = text.contains("accept") ||
                    text.contains("confirm") ||
                    text.contains("स्वीकार") ||
                    text.contains("take ride") ||
                    text.startsWith("accept in") ||
                    viewId.contains("confirm") ||
                    viewId.contains("accept") ||
                    viewId.contains("slide") ||
                    viewId.contains("btn_accept") ||
                    viewId.contains("primary_button")

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
        for (node in nodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()

            val isReject = text == "-" || text == "–" || text == "—" || text == "➖" ||
                    text == "✕" || text == "x" ||
                    text.contains("skip x") ||
                    text.contains("skip") ||
                    text.contains("reject") ||
                    text.contains("decline") ||
                    text.contains("dismiss") ||
                    text.contains("cancel") ||
                    viewId.contains("skip") ||
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

        // Rapido Minus Button Tap
        val rect = Rect()
        actionBtn.getBoundsInScreen(rect)
        if (rect.left > 120) {
            val minusX = (rect.left - 80).toFloat()
            val minusY = rect.centerY().toFloat()
            clickDirectCoordinate(minusX, minusY, null)
            return
        }

        // Uber Cross Tap
        val crossX = screenWidth * 0.85f
        val crossY = screenHeight * 0.38f
        clickDirectCoordinate(crossX, crossY, null)
    }

    private fun performAcceptClick(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val startX = if (rect.left > 0) rect.left.toFloat() + 60f else screenWidth * 0.20f
        val endX = if (rect.right > 0) rect.right.toFloat() - 60f else screenWidth * 0.85f
        val centerY = if (rect.centerY() > 0) rect.centerY().toFloat() else screenHeight * 0.42f

        // 1. Accessibility Click (Instant)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // 2. Instant Left-to-Right Swipe for Porter (50ms duration)
        val swipePath = Path().apply {
            moveTo(startX, centerY)
            lineTo(endX, centerY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)

        // 3. Instant Tap Backup for Uber/Rapido
        val tapPath = Path().apply {
            moveTo((startX + endX) / 2f, centerY)
        }
        val tapGesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath, 0, 30))
            .build()
        dispatchGesture(tapGesture, null, null)
    }

    private fun performClickAction(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val x = if (rect.centerX() > 0) rect.centerX().toFloat() else screenWidth * 0.5f
        val y = if (rect.centerY() > 0) rect.centerY().toFloat() else screenHeight * 0.90f
        clickDirectCoordinate(x, y, node)
    }

    private fun clickDirectCoordinate(x: Float, y: Float, node: AccessibilityNodeInfo?) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun onInterrupt() {
        Log.e(TAG, "Service Interrupted")
    }
}
