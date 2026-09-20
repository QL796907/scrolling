# 自动上滑

本机 Android 工具：点「开」后按随机间隔向上滑动屏幕，用来刷短视频。

- 包名：`com.hy.autoswipe`
- 当前版本：1.6（`versionCode` 7）

## 使用

1. 开启无障碍（「自动上滑」）
2. 允许悬浮窗
3. 建议关闭电池优化并允许自启动
4. 侧边栏点「开」开始，点「停」暂停；往屏幕外拖可收起

## 构建

用 Android Studio 打开本仓库，连接手机后 Run。  
或在本机执行 `build-apk.ps1`（需已安装 JDK 17 与 Android SDK）。

## 仓库说明

源码在 `main` 分支。发版时把 APK 和 `version.json` 挂到 GitHub Release，供应用内检查更新使用。
