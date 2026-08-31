package com.example.ridebot.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RideAccessibilityService : AccessibilityService() {

    companion object {
        var isBotRunning: Boolean = true
        var rejectBelowFare: Double = 0.0
        var acceptAboveFare: Double = 40.0
        var isFastTurboMode: Boolean = true
        var isAntiBotEnabled: Boolean = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isBotRunning || event == null) return

        val rootNode = rootInActiveWindow ?: return
        processScreenNodes(rootNode)
    }

    private fun processScreenNodes(node: AccessibilityNodeInfo) {
        val textList = mutableListOf<String>()
        collectAllText(node, textList)

        val fullScreenText = textList.joinToString(" ")
        val fare = extractFare(fullScreenText)

        if (fare != null) {
            when {
                fare < rejectBelowFare -> {
                    findAndClickButton(node, listOf("reject", "decline", "cancel", "ignore", "cross"))
                }
                fare >= acceptAboveFare -> {
                    findAndClickButton(node, listOf("accept", "swipe to accept", "tap to accept", "book ride", "accept order"))
                }
            }
        }
    }

    private fun collectAllText(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        node.text?.let { list.add(it.toString()) }
        node.contentDescription?.let { list.add(it.toString()) }
        for (i in 0 until node.childCount) {
            collectAllText(node.getChild(i), list)
        }
    }

    private fun extractFare(text: String): Double? {
        val regex = Regex("""(?:₹|Rs\.?|INR)\s*([0-9]+(?:\.[0-9]{1,2})?)""")
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun findAndClickButton(root: AccessibilityNodeInfo, targetTexts: List<String>) {
        for (target in targetTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(target)
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                } else {
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            return
                        }
                        parent = parent.parent
                    }
                }
            }
        }
    }

    override fun onInterrupt() {}
}
