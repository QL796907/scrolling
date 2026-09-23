package com.hy.autoswipe

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        // 不在 Application 里拉起悬浮窗：Activity 窗口还没稳定时叠 TYPE_APPLICATION_OVERLAY
        // 会让状态栏反复重绘。主界面第一帧后再 ensureRunning。
    }
}
