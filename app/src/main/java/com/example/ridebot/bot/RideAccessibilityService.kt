private fun performAcceptClick(node: AccessibilityNodeInfo) {
    val rect = Rect()
    node.getBoundsInScreen(rect)

    // Button boundaries calculate karein
    val startX = if (rect.left > 0) rect.left.toFloat() + 60f else screenWidth * 0.20f
    val endX = if (rect.right > 0) rect.right.toFloat() - 60f else screenWidth * 0.85f
    val centerY = if (rect.centerY() > 0) rect.centerY().toFloat() else screenHeight * 0.42f

    // 1. Direct Instant Accessibility Clicks (0ms wait)
    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    // 2. Ultra-Fast Left-to-Right Swipe Gesture (50ms duration - zero lag)
    val swipePath = Path().apply {
        moveTo(startX, centerY)
        lineTo(endX, centerY)
    }

    val gesture = GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 50))
        .build()

    dispatchGesture(gesture, null, null)

    // 3. Instant direct touch backup
    val tapPath = Path().apply {
        moveTo((startX + endX) / 2f, centerY)
    }
    val tapGesture = GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(tapPath, 0, 30))
        .build()
    dispatchGesture(tapGesture, null, null)
}
