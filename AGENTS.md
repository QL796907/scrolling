# 自动上滑

本机 Android 工具。点「开」后按随机间隔向上滑动屏幕。

- 本地目录：`scrolling`
- 包名：`com.hy.autoswipe`（不要随文件夹改名，否则手机无法覆盖安装）
- 版本：1.7（`versionCode` 8）
- 仓库：https://github.com/QL796907/-

## 构建

- Android Studio Run，或仓库根目录执行 `build-apk.ps1`
- 需要 JDK 17 与 Android SDK

## 发版

用户明确说发版时：

1. `app/build.gradle.kts` 的 `versionCode` +1，并改 `versionName`
2. 同步 `version.json`
3. 打出 `autoswipe.apk`
4. GitHub Release 同时挂 `autoswipe.apk` 和 `version.json`

检查更新地址：`https://github.com/QL796907/-/releases/latest/download/version.json`  
仓库需 Public，手机才能不登录下载。
