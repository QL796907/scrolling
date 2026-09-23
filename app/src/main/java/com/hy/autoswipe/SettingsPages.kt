package com.hy.autoswipe

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** 打开系统设置页。任何一跳失败都试下一个，避免 ActivityNotFound / SecurityException 把 App 打崩。 */
object SettingsPages {
    fun openAppDetails(context: Context): Boolean {
        val pkg = context.packageName
        return launch(
            context,
            listOf(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                    Uri.fromParts("package", pkg, null),
                ),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                    Uri.parse("package:$pkg"),
                ),
                Intent(Settings.ACTION_APPLICATION_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            ),
            "无法打开应用详情，请到系统设置里找到「自动上滑」",
        )
    }

    fun openAccessibility(context: Context): Boolean {
        return launch(
            context,
            listOf(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                Intent("android.settings.ACCESSIBILITY_SETTINGS"),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                    Uri.fromParts("package", context.packageName, null),
                ),
                Intent(Settings.ACTION_SETTINGS),
            ),
            "无法打开无障碍设置，请到系统设置 → 无障碍 → 找到「自动上滑」并打开",
        )
    }

    fun openOverlay(context: Context): Boolean {
        val pkg = context.packageName
        return launch(
            context,
            listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(
                    Uri.fromParts("package", pkg, null),
                ),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                    Uri.fromParts("package", pkg, null),
                ),
                Intent(Settings.ACTION_SETTINGS),
            ),
            "无法打开悬浮窗设置，请到系统设置里允许「显示在其他应用上层」",
        )
    }

    fun openBattery(context: Context): Boolean {
        val pkg = context.packageName
        return launch(
            context,
            listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(
                    Uri.parse("package:$pkg"),
                ),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                    Uri.fromParts("package", pkg, null),
                ),
                Intent(Settings.ACTION_SETTINGS),
            ),
            "无法打开电池设置，请到系统设置里把「自动上滑」设为无限制",
        )
    }

    fun launch(context: Context, intents: List<Intent>, failToast: String): Boolean {
        val activity = context as? Activity
        val fromActivity = activity != null && !activity.isFinishing
        for (raw in intents) {
            val first = Intent(raw)
            if (!fromActivity) {
                first.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(context, first)) return true
            val withNewTask = Intent(raw).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (tryStart(context, withNewTask)) return true
        }
        Toast.makeText(context.applicationContext, failToast, Toast.LENGTH_LONG).show()
        return false
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
