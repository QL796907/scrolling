package com.hy.autoswipe

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val note: String,
    val mirrors: List<String> = emptyList(),
    val sha256: String = "",
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

object AppUpdater {
    const val GITHUB_OWNER = "QL796907"
    const val GITHUB_REPO = "scrolling"
    const val VERSION_JSON_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest/download/version.json"
    const val MIRROR_DOCS_URL = "https://gh.4o.pw/docs"
    // 用法见 https://gh.4o.pw/docs ：前缀 + 完整 GitHub HTTPS 地址
    private const val MIRROR_PREFIX = "https://gh.4o.pw/"
    private const val LATEST_API =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val USER_AGENT = "AutoSwipe-Updater"
    private const val PROVIDER = ".fileprovider"
    private const val PREFS = "update"
    private const val KEY_USE_MIRROR = "use_github_mirror"

    fun currentVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    fun apkFile(context: Context): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        return File(dir, "update.apk")
    }

    fun canInstallPackages(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun check(context: Context): UpdateCheckResult {
        return try {
            val token = githubToken(context)
            val info = if (token.isNullOrBlank()) {
                parseUpdateInfo(httpGetFirst(candidateUrls(VERSION_JSON_URL, useMirror = useMirror(context)), null))
            } else {
                try {
                    fetchViaApi(token)
                } catch (_: Exception) {
                    parseUpdateInfo(
                        httpGetFirst(candidateUrls(VERSION_JSON_URL, useMirror = useMirror(context)), token),
                    )
                }
            }
            if (info.versionCode > currentVersionCode(context)) {
                UpdateCheckResult.Available(info)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (error: Exception) {
            UpdateCheckResult.Failed(describeError(error))
        }
    }

    fun download(
        context: Context,
        info: UpdateInfo,
        dest: File,
        cancelled: () -> Boolean,
        onProgress: (Int) -> Unit,
    ) {
        val token = githubToken(context)
        val primary = if (token.isNullOrBlank()) {
            info.apkUrl
        } else {
            try {
                resolveAssetApiUrl(token, info)
            } catch (_: Exception) {
                info.apkUrl
            }
        }
        var last: Exception? = null
        for (url in candidateUrls(primary, info.mirrors, useMirror(context))) {
            try {
                onProgress(0)
                downloadTo(url, dest, tokenFor(url, token), cancelled, onProgress)
                val expected = info.sha256
                if (expected.isNotBlank() && !sha256Hex(dest).equals(expected, ignoreCase = true)) {
                    dest.delete()
                    last = IllegalStateException("bad-sha256")
                    continue
                }
                return
            } catch (error: InterruptedException) {
                dest.delete()
                throw error
            } catch (error: Exception) {
                dest.delete()
                last = error
            }
        }
        throw last ?: IllegalStateException("all-sources-failed")
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + PROVIDER, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { resolve ->
                context.grantUriPermission(resolve.activityInfo.packageName, uri, flags)
            }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun githubToken(context: Context): String? =
        context.getString(R.string.github_token).trim().ifBlank { null }

    private fun fetchViaApi(token: String): UpdateInfo {
        val release = JSONObject(httpGet(LATEST_API, token, "application/vnd.github+json"))
        val assets = release.getJSONArray("assets")
        var versionJsonUrl: String? = null
        var apkApiUrl: String? = null
        var apkBrowserUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            val apiUrl = asset.optString("url")
            val browserUrl = asset.optString("browser_download_url")
            if (name == "version.json") versionJsonUrl = apiUrl
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkApiUrl = apiUrl
                apkBrowserUrl = browserUrl
            }
        }
        if (versionJsonUrl.isNullOrBlank()) {
            throw IllegalStateException("missing-version-json")
        }
        val info = parseUpdateInfo(httpGetAsset(versionJsonUrl, token, asText = true))
        val apkUrl = apkApiUrl?.takeIf { it.isNotBlank() } ?: info.apkUrl.ifBlank { apkBrowserUrl.orEmpty() }
        if (apkUrl.isBlank()) throw IllegalStateException("missing-apk")
        return info.copy(apkUrl = apkUrl)
    }

    private fun resolveAssetApiUrl(token: String, info: UpdateInfo): String {
        if (info.apkUrl.contains("api.github.com")) return info.apkUrl
        return try {
            fetchViaApi(token).apkUrl
        } catch (_: Exception) {
            info.apkUrl
        }
    }

    private fun parseUpdateInfo(body: String): UpdateInfo {
        val trimmed = body.trim()
        if (trimmed.startsWith("<") || trimmed.startsWith("{").not()) {
            throw IllegalStateException("private-or-html")
        }
        val json = JSONObject(trimmed)
        val mirrors = mutableListOf<String>()
        json.optJSONArray("apkMirrors")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optString(index).trim()
                if (item.isNotBlank()) mirrors += item
            }
        }
        return UpdateInfo(
            versionCode = json.getInt("versionCode"),
            versionName = json.getString("versionName"),
            apkUrl = json.getString("apkUrl"),
            note = json.optString("note").trim(),
            mirrors = mirrors,
            sha256 = json.optString("apkSha256").trim(),
        )
    }

    fun useMirror(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_MIRROR, true)

    fun setUseMirror(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_MIRROR, enabled)
            .apply()
    }

    /** GitHub 镜像在前（若勾选），官方与 apkMirrors 随后。Token 不会发给镜像。 */
    internal fun candidateUrls(
        primary: String,
        extras: List<String> = emptyList(),
        useMirror: Boolean = true,
    ): List<String> {
        val urls = LinkedHashSet<String>()
        val githubSources = (listOf(primary) + extras).filter { isGithubFileUrl(it) }
        if (useMirror) {
            githubSources.forEach { source -> urls += wrapMirror(source) }
        }
        if (primary.isNotBlank()) urls += primary
        extras.forEach { item -> if (item.isNotBlank()) urls += item }
        return urls.toList()
    }

    private fun isGithubFileUrl(url: String): Boolean =
        url.contains("github.com", ignoreCase = true) ||
            url.contains("githubusercontent.com", ignoreCase = true)

    private fun wrapMirror(url: String): String {
        if (url.startsWith(MIRROR_PREFIX)) return url
        return MIRROR_PREFIX + url
    }

    private fun httpGetFirst(urls: List<String>, token: String?, accept: String = "application/json"): String {
        var last: Exception? = null
        for (url in urls) {
            try {
                return httpGet(url, tokenFor(url, token), accept)
            } catch (error: Exception) {
                last = error
            }
        }
        throw last ?: IllegalStateException("all-sources-failed")
    }

    private fun tokenFor(url: String, token: String?): String? {
        if (token.isNullOrBlank()) return null
        val host = try {
            URL(url).host
        } catch (_: Exception) {
            return null
        }
        return if (host == "github.com" || host.endsWith(".github.com")) token else null
    }

    private fun downloadTo(
        url: String,
        dest: File,
        token: String?,
        cancelled: () -> Boolean,
        onProgress: (Int) -> Unit,
    ) {
        dest.parentFile?.mkdirs()
        val temp = File(dest.parentFile, "${dest.name}.part")
        if (temp.exists()) temp.delete()
        val connection = openFollowing(url, token, if (token != null) "application/octet-stream" else "*/*")
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read = 0L
                    var lastPercent = -1
                    while (true) {
                        if (cancelled()) throw InterruptedException("cancelled")
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            val percent = ((read * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            onProgress(100)
            if (dest.exists()) dest.delete()
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGet(url: String, token: String?, accept: String = "application/json"): String {
        val connection = openFollowing(url, token, accept)
        try {
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGetAsset(url: String, token: String, asText: Boolean): String {
        val accept = if (asText) "application/octet-stream" else "*/*"
        val connection = openFollowing(url, token, accept)
        try {
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun openFollowing(url: String, token: String?, accept: String): HttpURLConnection {
        var current = url
        var hops = 0
        while (hops < 8) {
            hops += 1
            val host = URL(current).host
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", accept)
                if (!token.isNullOrBlank() && host.endsWith("github.com")) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            val code = connection.responseCode
            if (code in 200..299) return connection
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IllegalStateException("http-$code")
                current = URL(URL(current), location).toString()
                continue
            }
            connection.disconnect()
            throw IllegalStateException("http-$code")
        }
        throw IllegalStateException("http-redirect")
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun describeError(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            error is InterruptedException -> "已取消下载"
            text.contains("bad-sha256") -> "下载的安装包校验失败，已取消安装"
            error is UnknownHostException || error is SocketTimeoutException ||
                text.contains("all-sources-failed") ->
                "GitHub 和镜像都连不上，请稍后再试，或改一下「使用镜像下载」"
            text.contains("http-401") || text.contains("http-403") || text.contains("private-or-html") ->
                "仓库是私有的。请把 GitHub 仓库设为 Public，或在 strings.xml 填入 github_token。"
            text.contains("http-404") || text.contains("missing-") ->
                "没有检测到新版本。请先把仓库设为 Public，并发布带 version.json 和 APK 的 Release。"
            else -> "检查更新失败，请稍后重试"
        }
    }
}
