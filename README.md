# 自动上滑

本机 Android 工具：点「开」后按随机间隔向上滑动屏幕，用来刷短视频。

- 包名：`com.hy.autoswipe`
- 当前版本：1.7（`versionCode` 8）
- 仓库：[https://github.com/QL796907/-](https://github.com/QL796907/-)

## 使用

1. 开启无障碍（「自动上滑」）
2. 允许悬浮窗
3. 建议关闭电池优化并允许自启动
4. 侧边栏点「开」开始，点「停」暂停；往屏幕外拖可收起

## 构建

用 Android Studio 打开本仓库，连接手机后 Run。  
或在本机执行 `build-apk.ps1`（需已安装 JDK 17 与 Android SDK）。

## 应用内更新

主界面会自动检查 GitHub 上的最新 Release，也可以点「检查更新」。

发版步骤：

1. 把 `app/build.gradle.kts` 里的 `versionCode` 加 1，并改 `versionName`
2. 同步修改仓库根目录的 `version.json`
3. 打包 APK，文件名改为 `autoswipe.apk`
4. 在 GitHub 新建 Release，同时挂上 `autoswipe.apk` 和 `version.json`
5. 手机打开应用即可检查、下载、安装

**仓库需要设为 Public**，手机才能不登录就下载。现在如果仍是 Private，检查更新会失败。  
也可以在 `app/src/main/res/values/strings.xml` 的 `github_token` 里填 GitHub PAT（只适合自己用的包，不要把带 token 的 APK 发给别人）。
