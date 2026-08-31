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
        var isFastTurboMode = false
        var isAntiBotEnabled = true
        var minTargetFare: Double = 1.0
        var maxTargetFare: Double = 2000.0
        var maxPickupDistanceKm: Double = 2.5 // Default 2.5 km (0.0 km to 5.0 km)

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
        if (currentTime - lastActionTimestamp < 2500) return

        val pkgName = event?.packageName?.toString() ?: ""
        val appDetected = when {
            pkgName.contains("rapido", ignoreCase = true) -> "Rapido"
            pkgName.contains("ubercab.driver", ignoreCase = true) || pkgName.contains("uber", ignoreCase = true) -> "Uber Driver"
            pkgName.contains("olacabs.partner", ignoreCase = true) -> "Ola Partner"
            pkgName.contains("porter", ignoreCase = true) -> "Porter"
            else -> ""
        }

        if (appDetected.isEmpty()) return

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

        val isRealRideCard = verifyIncomingRideOffer(allNodes, appDetected)
        if (!isRealRideCard) return

        val actionButton = findActionTarget(allNodes) ?: return
        val detectedFare = parseFareFromOfferCard(allNodes)

        if (detectedFare == null || detectedFare <= 0.0) return

        val rideDetails = parseRideDetails(allNodes)
        val pickupKmValue = extractKmNumber(rideDetails.pickupDist)

        lastActionTimestamp = currentTime

        Log.d(TAG, "Valid Offer on $appDetected! Fare: ₹$detectedFare | Pickup Dist: ${rideDetails.pickupDist} ($pickupKmValue km) | Max Allowed Pickup: $maxPickupDistanceKm km")

        // 🟢 AGAR BOT ON HAI
        if (isBotRunning) {
            val isFareInRange = detectedFare in minTargetFare..maxTargetFare
            val isPickupInRange = (pickupKmValue == null) || (pickupKmValue <= maxPickupDistanceKm)

            if (isFareInRange && isPickupInRange) {
                // ACCEPT RIDE
                performClickAction(actionButton)
                addLog(
                    appName = appDetected,
                    fare = detectedFare,
                    pickupDist = rideDetails.pickupDist,
                    dropDist = rideDetails.dropDist,
                    pickupLoc = rideDetails.pickupLoc,
                    dropLoc = rideDetails.dropLoc,
                    status = RideStatus.AUTO_ACCEPTED,
                    note = "Accepted: Fare ₹$detectedFare | Pickup ${rideDetails.pickupDist} <= $maxPickupDistanceKm km"
                )
                return
            } else {
                // REJECT RIDE
                val rejectReason = if (!isFareInRange) "Fare ₹$detectedFare out of range" else "Pickup ${rideDetails.pickupDist} > $maxPickupDistanceKm km limit"
                executeRejectAction(allNodes, actionButton)
                addLog(
                    appName = appDetected,
                    fare = detectedFare,
                    pickupDist = rideDetails.pickupDist,
                    dropDist = rideDetails.dropDist,
                    pickupLoc = rideDetails.pickupLoc,
                    dropLoc = rideDetails.dropLoc,
                    status = RideStatus.REJECTED,
                    note = "Rejected: $rejectReason"
                )
                return
            }
        }
    }

    private fun extractKmNumber(distStr: String): Double? {
        if (distStr.isBlank()) return null
        val regex = Regex("""(\d+(?:\.\d+)?)\s*km""", RegexOption.IGNORE_CASE)
        val match = regex.find(distStr)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun verifyIncomingRideOffer(nodes: List<AccessibilityNodeInfo>, app: String): Boolean {
        val texts = nodes.mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }
            .map { it.trim().lowercase() }

        val hasTripIndicator = texts.any {
            it.contains("order") || 
            it.contains("trip") || 
            it.contains("bike") || 
            it.contains("auto") || 
            it.contains("mins") || 
            it.contains("min") || 
            it.contains("km") || 
            it.contains("cash payment") ||
            it.contains("accept") ||
            it.contains("confirm")
        }

        val hasActionButton = nodes.any { node ->
            val t = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            t == "accept" || t == "confirm" || t == "स्वीकार"
        }

        return hasTripIndicator && hasActionButton
    }

    private fun parseFareFromOfferCard(nodes: List<AccessibilityNodeInfo>): Double? {
        val texts = nodes.mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }
            .filter { it.isNotBlank() }

        for (t in texts) {
            val clean = t.trim()
            if ((clean.contains("₹") || clean.contains("Rs", ignoreCase = true) || clean.contains("INR", ignoreCase = true)) &&
                !clean.contains("/hr", ignoreCase = true) &&
                !clean.contains("Premium", ignoreCase = true) &&
                !clean.contains("active hr", ignoreCase = true) &&
                !clean.contains("today", ignoreCase = true) &&
                !clean.contains("balance", ignoreCase = true)
            ) {
                val regex = Regex("""(?:₹|Rs\.?|INR)\s*(\d+(?:,\d+)*(?:\.\d+)?)""")
                val match = regex.find(clean)
                if (match != null) {
                    val num = match.groupValues[1].replace(",", "").toDoubleOrNull()
                    if (num != null && num in 10.0..5000.0) return num
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
                 t.contains("Road", ignoreCase = true) ||
                 t.contains("Sector", ignoreCase = true) ||
                 t.contains("Gali", ignoreCase = true) ||
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

            val isAcceptOrConfirm = text == "confirm" ||
                    text == "accept" ||
                    text.contains("confirm") ||
                    text.contains("accept") ||
                    text.contains("स्वीकार") ||
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

        // Rapido Minus Fallback
        val rect = Rect()
        actionBtn.getBoundsInScreen(rect)
        if (rect.left > 120) {
            val minusX = (rect.left - 80).toFloat()
            val minusY = rect.centerY().toFloat()
            clickDirectCoordinate(minusX, minusY, null)
            return
        }

        // Uber Cross Fallback
        val crossX = screenWidth * 0.85f
        val crossY = screenHeight * 0.42f
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
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 30))
                .build()
            dispatchGesture(gesture, null, null)

            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }, delay)
    }

    override fun onInterrupt() {
        Log.e(TAG, "Service Interrupted")
    }
}
