package com.hy.autoswipe

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.hy.autoswipe.databinding.OverlayPanelBinding
import kotlin.random.Random

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var binding: OverlayPanelBinding
    private val handler = Handler(Looper.getMainLooper())
    private var animator: ValueAnimator? = null

    private var running = false
    private var remainingSeconds = 0
    private var dockRight = true
    private var peeked = false
    private var panelAttached = false
    private var connecting = false
    private var dragAccumX = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            remainingSeconds -= 1
            if (remainingSeconds <= 0) {
                performSwipe()
                remainingSeconds = nextInterval()
            }
            render()
            handler.postDelayed(this, 1_000L)
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (SwipeAccessibilityService.isEnabled(this@OverlayService)) {
                PermissionStore.markAccessibilityGranted(this@OverlayService)
            }
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        startInForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // 用户点过「收起」后，进程被杀再拉起也不要自动把侧边栏弹回来
        if (!wasHiddenByUser()) {
            showOverlay()
        }
        handler.post(watchdog)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSwiping()
            ACTION_PAUSE -> pauseSwiping()
            ACTION_STOP, ACTION_HIDE -> hidePanel()
            ACTION_SHOW -> showOverlay()
            else -> if (!wasHiddenByUser()) showOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pauseSwiping()
        animator?.cancel()
        handler.removeCallbacks(watchdog)
        detachPanel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun showOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        if (panelAttached) return
        if (!::windowManager.isInitialized) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        }
        if (!::binding.isInitialized) {
            inflateOverlay()
        }
        try {
            windowManager.addView(binding.root, params)
            panelAttached = true
            setHiddenByUser(false)
            binding.root.post { applyDock(animate = false) }
        } catch (_: Exception) {
        }
    }

    fun hidePanel() {
        setHiddenByUser(true)
        detachPanel()
    }

    private fun detachPanel() {
        if (!panelAttached || !::binding.isInitialized) return
        try {
            windowManager.removeView(binding.root)
        } catch (_: Exception) {
        }
        panelAttached = false
    }

    private fun inflateOverlay() {
        binding = OverlayPanelBinding.inflate(LayoutInflater.from(this))

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_LAYOUT_NO_LIMITS 会把窗口画进状态栏，MIUI 上时间/电量会不停闪
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = overlayStartX()
            y = overlayStartY()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }

        binding.btnStart.setOnClickListener { startSwiping() }
        binding.btnPause.setOnClickListener { pauseSwiping() }

        val drag = { dx: Int, dy: Int ->
            animator?.cancel()
            dragAccumX += dx
            val minX = 0
            val maxX = (screenWidth() - binding.root.width.coerceAtLeast(dp(16))).coerceAtLeast(0)
            params.x = (params.x + dx).coerceIn(minX, maxX)
            params.y = (params.y + dy).coerceIn(
                statusBarInset(),
                (screenHeight() - binding.root.height.coerceAtLeast(dp(44))).coerceAtLeast(statusBarInset()),
            )
            try {
                windowManager.updateViewLayout(binding.root, params)
            } catch (_: Exception) {
            }
        }
        val dragEnd = { moved: Boolean ->
            if (!moved && peeked) {
                peeked = false
                dragAccumX = 0
                applyDock(animate = true)
            } else {
                settleAfterDrag()
            }
        }

        val panel = binding.panel
        panel.onPointerDown = { dragAccumX = 0 }
        panel.onDrag = drag
        panel.onDragEnd = dragEnd
        enableArrowDrag(drag, dragEnd)
        render()
    }

    private fun enableArrowDrag(
        drag: (Int, Int) -> Unit,
        dragEnd: (Boolean) -> Unit,
    ) {
        var startX = 0f
        var startY = 0f
        var lastX = 0f
        var lastY = 0f
        var moved = false
        binding.edgeArrow.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    lastX = event.rawX
                    lastY = event.rawY
                    moved = false
                    dragAccumX = 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastX).toInt()
                    val dy = (event.rawY - lastY).toInt()
                    lastX = event.rawX
                    lastY = event.rawY
                    if (kotlin.math.abs(event.rawX - startX) > 8 || kotlin.math.abs(event.rawY - startY) > 8) {
                        moved = true
                    }
                    if (moved) drag(dx, dy)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragEnd(moved)
                    true
                }
                else -> false
            }
        }
    }

    private fun settleAfterDrag() {
        val screenW = screenWidth()
        val width = binding.root.width.takeIf { it > 0 } ?: dp(52)
        val center = params.x + width / 2
        dockRight = center >= screenW / 2
        // 不能再拖出屏幕外，改为：朝贴边方向多拖一段就收成箭头
        val outward = if (dockRight) dragAccumX > dp(24) else dragAccumX < -dp(24)
        peeked = outward
        dragAccumX = 0
        applyDock(animate = true)
    }

    private fun applyPeekUi() {
        binding.panel.visibility = if (peeked) View.GONE else View.VISIBLE
        binding.edgeArrow.visibility = if (peeked) View.VISIBLE else View.GONE
        binding.edgeArrow.text = if (dockRight) "‹" else "›"
        binding.edgeArrow.setBackgroundResource(
            if (dockRight) R.drawable.bg_arrow_right else R.drawable.bg_arrow_left,
        )
        binding.panel.interceptAllTouches = false
    }

    private fun applyDock(animate: Boolean) {
        if (!::binding.isInitialized || !panelAttached) return
        applyPeekUi()
        binding.root.post {
            if (!panelAttached || !::binding.isInitialized) return@post
            val width = binding.root.width.takeIf { it > 0 } ?: if (peeked) dp(16) else dp(52)
            val targetX = if (dockRight) screenWidth() - width else 0
            val targetY = params.y.coerceIn(
                statusBarInset(),
                (screenHeight() - binding.root.height).coerceAtLeast(statusBarInset()),
            )
            moveTo(targetX, targetY, animate)
        }
    }

    private fun moveTo(targetX: Int, targetY: Int, animate: Boolean) {
        if (!animate) {
            params.x = targetX
            params.y = targetY
            try {
                windowManager.updateViewLayout(binding.root, params)
            } catch (_: Exception) {
            }
            return
        }
        animator?.cancel()
        val startX = params.x
        val startY = params.y
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener { value ->
                val t = value.animatedValue as Float
                params.x = (startX + (targetX - startX) * t).toInt()
                params.y = (startY + (targetY - startY) * t).toInt()
                try {
                    windowManager.updateViewLayout(binding.root, params)
                } catch (_: Exception) {
                }
            }
            start()
        }
    }

    private fun startSwiping() {
        val connected = SwipeAccessibilityService.instance
        if (connected != null) {
            beginRun()
            return
        }
        if (connecting) return
        if (SwipeAccessibilityService.isEnabled(this)) {
            connecting = true
            Toast.makeText(this, "正在连接无障碍…", Toast.LENGTH_SHORT).show()
            PermissionStore.waitForAccessibility(2500) { ok ->
                connecting = false
                if (ok) beginRun() else promptAccessibility()
            }
            return
        }
        promptAccessibility()
    }

    private fun beginRun() {
        if (running) return
        PermissionStore.markAccessibilityGranted(this)
        running = true
        remainingSeconds = nextInterval()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1_000L)
        vibrate()
        render()
        refreshNotification()
        Toast.makeText(this, "已开启，${remainingSeconds} 秒后滑一下", Toast.LENGTH_SHORT).show()
    }

    private fun promptAccessibility() {
        Toast.makeText(this, "请打开一次「自动上滑」无障碍，之后会保持开启", Toast.LENGTH_LONG).show()
        startActivity(
            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )
    }

    private fun pauseSwiping() {
        running = false
        handler.removeCallbacks(tick)
        render()
        refreshNotification()
    }

    private fun performSwipe() {
        val service = SwipeAccessibilityService.instance
        if (service == null) {
            pauseSwiping()
            Toast.makeText(this, "无障碍被系统关掉了，请重新打开", Toast.LENGTH_LONG).show()
            return
        }
        if (!service.swipeUp()) {
            Toast.makeText(this, "这次上滑没成功，将继续尝试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun render() {
        if (!::binding.isInitialized) return
        binding.btnStart.isEnabled = !running
        binding.btnStart.alpha = if (running) 0.4f else 1f
        binding.btnPause.isEnabled = running
        binding.btnPause.alpha = if (running) 1f else 0.4f
        binding.statusText.text = if (running) "${remainingSeconds}s" else "停"
        binding.statusText.setTextColor(
            getColor(if (running) R.color.success else R.color.ink_muted),
        )
    }

    private fun nextInterval(): Int {
        val (min, max) = IntervalStore(this).currentRange()
        return if (max <= min) min else Random.nextInt(min, max + 1)
    }

    fun reloadInterval() {
        if (!running) return
        remainingSeconds = nextInterval()
        render()
        refreshNotification()
    }

    private fun overlayStartX(): Int = screenWidth() - dp(60)

    private fun overlayStartY(): Int = statusBarInset() + dp(180)

    private fun statusBarInset(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val bar = if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
        return bar + dp(8)
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFY_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFY_ID, notification)
        }
    }

    private fun refreshNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFY_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val start = serviceAction(ACTION_START, 1)
        val pause = serviceAction(ACTION_PAUSE, 2)
        val stop = serviceAction(ACTION_STOP, 3)
        val text = if (running) {
            getString(R.string.notification_swiping) + " · ${remainingSeconds}s"
        } else {
            getString(R.string.notification_running)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_swipe_up)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "开始", start)
            .addAction(0, "暂停", pause)
            .addAction(0, "收起", stop)
            .build()
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ACTION_START = "com.hy.autoswipe.START"
        const val ACTION_PAUSE = "com.hy.autoswipe.PAUSE"
        const val ACTION_STOP = "com.hy.autoswipe.STOP"
        const val ACTION_SHOW = "com.hy.autoswipe.SHOW"
        const val ACTION_HIDE = "com.hy.autoswipe.HIDE"
        private const val CHANNEL_ID = "overlay_quiet"
        private const val NOTIFY_ID = 1001
        private const val PREFS = "overlay"
        private const val KEY_HIDDEN = "panel_hidden"

        @Volatile
        var instance: OverlayService? = null
            private set

        fun start(context: Context) {
            setHiddenPref(context, false)
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_SHOW)
            startServiceInternal(context, intent)
        }

        fun ensureRunning(context: Context) {
            if (instance != null) return
            startServiceInternal(context, Intent(context, OverlayService::class.java))
        }

        fun hide(context: Context) {
            setHiddenPref(context, true)
            instance?.hidePanel()
        }

        fun isPanelVisible(): Boolean = instance?.panelAttached == true

        private fun startServiceInternal(context: Context, intent: Intent) {
            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }

        private fun overlayPrefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun setHiddenPref(context: Context, hidden: Boolean) {
            overlayPrefs(context).edit().putBoolean(KEY_HIDDEN, hidden).apply()
        }
    }

    private fun wasHiddenByUser(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_HIDDEN, false)

    private fun setHiddenByUser(hidden: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_HIDDEN, hidden).apply()
    }
}
