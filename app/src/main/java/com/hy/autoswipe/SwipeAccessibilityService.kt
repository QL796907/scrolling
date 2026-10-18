package com.hy.autoswipe

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class SwipeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // 不在这里拉起悬浮窗。从最近任务划掉后进程常被无障碍重新拉起，
        // 若再 ensureRunning，侧边栏会自己出现。
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun swipeUp(): Boolean {
        return try {
            val pm = getSystemService(PowerManager::class.java)
            if (pm?.isInteractive == false) return true

            val (width, height) = displaySize()
            if (width <= 0 || height <= 0) return false

            val startX = width / 2f
            val startY = height * 0.72f
            val endY = height * 0.28f
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(startX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 110)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        @Volatile
        var instance: SwipeAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            // 系统这项是冒号分隔的组件名，只按两种扁平写法精确匹配。
            val component = ComponentName(context, SwipeAccessibilityService::class.java)
            val expected = setOf(component.flattenToString(), component.flattenToShortString())
            val raw = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return raw.split(':').any { token -> token.trim() in expected }
        }
    }
}
