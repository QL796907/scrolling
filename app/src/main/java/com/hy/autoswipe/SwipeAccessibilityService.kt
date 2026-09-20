package com.hy.autoswipe

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

class SwipeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        PermissionStore.markAccessibilityGranted(this)
        try {
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 100
                flags = flags or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        } catch (_: Exception) {
        }
        try {
            if (Settings.canDrawOverlays(this)) {
                OverlayService.start(this)
            }
        } catch (_: Exception) {
        }
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

            val (width, height) = screenSize()
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

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    companion object {
        @Volatile
        var instance: SwipeAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            val pkg = context.packageName
            val expected = SwipeAccessibilityService::class.java.name
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            )
            if (enabledServices.any { info ->
                    val name = info.resolveInfo?.serviceInfo?.name ?: return@any false
                    val servicePkg = info.resolveInfo?.serviceInfo?.packageName ?: return@any false
                    servicePkg == pkg && (
                        name == expected ||
                            name.endsWith(".SwipeAccessibilityService") ||
                            name.contains("SwipeAccessibilityService")
                        )
                }
            ) {
                return true
            }
            val raw = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return raw.split(':', ';', ',', '|').any { token ->
                val item = token.trim()
                if (item.isEmpty()) return@any false
                val matchesPkg = item.contains(pkg, ignoreCase = true)
                val matchesService = item.contains("SwipeAccessibilityService", ignoreCase = true)
                matchesPkg && matchesService
            }
        }
    }
}
