# 自动上滑

本机 Android 工具。点「开」后按随机间隔向上滑动屏幕。

- 本地目录：`scrolling`
- 包名：`com.hy.autoswipe`（不要随文件夹改名，否则手机无法覆盖安装）
- 版本：1.9（`versionCode` 10）
- 仓库：https://github.com/QL796907/scrolling

## 构建

- Android Studio Run，或仓库根目录执行 `build-apk.ps1`
- 需要 JDK 17 与 Android SDK

## 发版

用户明确说发版时：

1. `app/build.gradle.kts` 的 `versionCode` +1，并改 `versionName`
2. 同步 `version.json`
3. 打出 `autoswipe.apk`
4. 第一次发版写完整 README；之后每次发版只更新 README 里有变化的部分
5. GitHub Release 同时挂 `autoswipe.apk` 和 `version.json`

检查更新可勾选「使用镜像下载」（默认开），经 `https://gh.4o.pw/` 加速 GitHub 文件，说明见 https://gh.4o.pw/docs 。`version.json` 可加 `apkMirrors`、`apkSha256`。

检查更新地址：`https://github.com/QL796907/scrolling/releases/latest/download/version.json`  
仓库需 Public，手机才能不登录下载。
