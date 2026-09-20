package com.hy.autoswipe

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hy.autoswipe.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRestricted.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                },
            )
            Toast.makeText(
                this,
                "点右上角三个点 → 允许受限制的设置，然后返回再打开无障碍",
                Toast.LENGTH_LONG,
            ).show()
        }
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "找到「自动上滑」并打开开关", Toast.LENGTH_LONG).show()
        }
        binding.btnOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            startActivity(intent)
        }
        binding.btnBattery.setOnClickListener {
            requestUnrestrictedBattery()
        }
        binding.btnAutostart.setOnClickListener {
            OemSettings.openAutostart(this)
            Toast.makeText(this, "请允许「自动上滑」自启动和后台运行", Toast.LENGTH_LONG).show()
        }
        binding.btnLaunch.setOnClickListener { launchOverlay() }
        binding.btnStop.setOnClickListener {
            OverlayService.hide(this)
            refreshStatus()
        }
        binding.btnApplyInterval.setOnClickListener { applyInterval() }
        binding.btnManagePresets.setOnClickListener {
            startActivity(Intent(this, PresetsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        maybeAskNotificationPermission()
        maybeAskBatteryOnce()
        refreshStatus()
        renderInterval()
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
        if (SwipeAccessibilityService.isEnabled(this)) {
            PermissionStore.markAccessibilityGranted(this)
        }
    }

    private fun applyInterval() {
        val min = binding.inputMin.text.toString().toIntOrNull()
        val max = binding.inputMax.text.toString().toIntOrNull()
        if (min == null || max == null) {
            Toast.makeText(this, "请填写最小秒和最大秒", Toast.LENGTH_SHORT).show()
            return
        }
        if (min < 1 || max < 1) {
            Toast.makeText(this, "秒数至少为 1", Toast.LENGTH_SHORT).show()
            return
        }
        if (max < min) {
            Toast.makeText(this, "最大秒不能小于最小秒", Toast.LENGTH_SHORT).show()
            return
        }
        val store = IntervalStore(this)
        store.setCurrent(min, max)
        store.markCustomSelected()
        OverlayService.instance?.reloadInterval()
        renderInterval()
        Toast.makeText(this, "已设为 $min–${max} 秒，滑一下歇一下", Toast.LENGTH_SHORT).show()
    }

    private fun renderInterval() {
        val store = IntervalStore(this)
        val (min, max) = store.currentRange()
        if (binding.inputMin.text.toString() != min.toString()) {
            binding.inputMin.setText(min.toString())
        }
        if (binding.inputMax.text.toString() != max.toString()) {
            binding.inputMax.setText(max.toString())
        }
        binding.textCurrentInterval.text = "当前：$min–${max} 秒，滑一下歇一下"

        val selectedId = store.selectedId()
        binding.presetContainer.removeAllViews()
        store.presets().forEach { preset ->
            val item = com.hy.autoswipe.databinding.ItemPresetChoiceBinding.inflate(
                layoutInflater,
                binding.presetContainer,
                false,
            )
            val selected = preset.id == selectedId
            item.presetLabel.text = preset.label()
            item.root.setBackgroundResource(
                if (selected) R.drawable.bg_preset_selected else R.drawable.bg_preset,
            )
            item.root.setOnClickListener {
                store.selectPreset(preset.id)
                OverlayService.instance?.reloadInterval()
                renderInterval()
                Toast.makeText(this, "已选择 ${preset.label()}", Toast.LENGTH_SHORT).show()
            }
            binding.presetContainer.addView(item.root)
        }
    }

    private fun launchOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许显示悬浮窗", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        OverlayService.start(this)
        Toast.makeText(this, "侧边栏已打开。点「开」开始上滑，往外拖可以收起", Toast.LENGTH_LONG).show()
        refreshStatus()
    }

    private fun refreshStatus() {
        val accessibilityOn = SwipeAccessibilityService.isEnabled(this)
        val overlayOn = Settings.canDrawOverlays(this)
        val overlayRunning = OverlayService.isPanelVisible()

        if (accessibilityOn) PermissionStore.markAccessibilityGranted(this)

        binding.statusAccessibility.text = if (accessibilityOn) "已开启" else "未开启"
        binding.statusAccessibility.setTextColor(color(if (accessibilityOn) R.color.success else R.color.warn))
        binding.btnAccessibility.text = if (accessibilityOn) "已完成" else "去开启无障碍"
        binding.btnRestricted.visibility =
            if (accessibilityOn) android.view.View.GONE else android.view.View.VISIBLE

        binding.statusOverlay.text = if (overlayOn) "已允许" else "未允许"
        binding.statusOverlay.setTextColor(color(if (overlayOn) R.color.success else R.color.warn))
        binding.btnOverlay.text = if (overlayOn) "已完成" else "去允许"

        binding.statusBattery.text = if (isIgnoringBattery()) "无限制" else "建议关闭限制"
        binding.statusBattery.setTextColor(color(if (isIgnoringBattery()) R.color.success else R.color.ink_muted))

        binding.btnLaunch.isEnabled = overlayOn
        binding.btnLaunch.text = when {
            overlayRunning -> "侧边栏已显示"
            overlayOn -> "显示侧边栏"
            else -> "请先允许悬浮窗"
        }
        binding.btnStop.isEnabled = overlayRunning
    }

    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybeAskBatteryOnce() {
        if (isIgnoringBattery() || !PermissionStore.shouldAskBattery(this)) return
        if (!Settings.canDrawOverlays(this) || !SwipeAccessibilityService.isEnabled(this)) return
        PermissionStore.markBatteryAsked(this)
        requestUnrestrictedBattery()
    }

    private fun requestUnrestrictedBattery() {
        if (isIgnoringBattery()) {
            Toast.makeText(this, "电池限制已经关闭", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun isIgnoringBattery(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun color(id: Int): Int = ContextCompat.getColor(this, id)
}
