package com.uc0079.launcher

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer versionCode and downloads the APK.
 *
 * Release tag convention: "v{versionCode}" (e.g. "v6").
 */
object UpdateChecker {

    private const val OWNER = "pv-dn"
    private const val REPO = "gundam-os"
    private const val APK_NAME = "gundam-os.apk"
    private const val API_LATEST =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    /** Public page used as a last-resort parse target if the API is blocked. */
    private const val RELEASES_HTML =
        "https://github.com/$OWNER/$REPO/releases/latest"

    data class UpdateInfo(
        val available: Boolean,
        val latestTag: String = "",
        val apkUrl: String = "",
        val message: String = ""
    )

    fun currentVersionCode(context: Context): Int {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) {
            pi.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode
        }
    }

    /** Run on IO dispatcher. Retries a few times. Returns null on hard failure. */
    suspend fun check(currentVersionCode: Int): UpdateInfo? =
        withContext(Dispatchers.IO) {
            repeat(3) { attempt ->
                val info = runCatching { checkOnce(currentVersionCode) }.getOrNull()
                if (info != null) return@withContext info
                if (attempt < 2) delay(1_500L * (attempt + 1))
            }
            null
        }

    private fun checkOnce(currentVersionCode: Int): UpdateInfo? {
        // 1) Official GitHub API
        fetchJson(API_LATEST)?.let { json ->
            parseReleaseJson(json, currentVersionCode)?.let { return it }
        }
        // 2) Fallback: redirect URL of /releases/latest contains the tag
        val tag = resolveLatestTagFromHtml() ?: return null
        val remoteCode = tag.trimStart('v').toIntOrNull() ?: return null
        val apkUrl =
            "https://github.com/$OWNER/$REPO/releases/download/$tag/$APK_NAME"
        return UpdateInfo(
            available = remoteCode > currentVersionCode,
            latestTag = tag,
            apkUrl = apkUrl,
            message = "v$currentVersionCode → $tag"
        )
    }

    private fun parseReleaseJson(body: String, currentVersionCode: Int): UpdateInfo? {
        val json = JSONObject(body)
        val tag = json.getString("tag_name")
        val apkUrl = json.getJSONArray("assets")
            .let { arr ->
                (0 until arr.length()).mapNotNull { arr.getJSONObject(it) }
                    .firstOrNull { it.getString("name") == APK_NAME }
                    ?.getString("browser_download_url")
            } ?: return null
        val remoteCode = tag.trimStart('v').toIntOrNull() ?: 1
        return UpdateInfo(
            available = remoteCode > currentVersionCode,
            latestTag = tag,
            apkUrl = apkUrl,
            message = "v$currentVersionCode → $tag"
        )
    }

    private fun fetchJson(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            // GitHub rejects requests without a User-Agent.
            setRequestProperty("User-Agent", "Z-Gundam-OS-Launcher")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.readText()?.takeIf { code in 200..299 }
        } finally {
            conn.disconnect()
        }
    }

    /** Follows /releases/latest redirect; path ends with /tag/vN. */
    private fun resolveLatestTagFromHtml(): String? {
        val conn = (URL(RELEASES_HTML).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Z-Gundam-OS-Launcher")
        }
        return try {
            val loc = conn.getHeaderField("Location")
                ?: conn.url.toString()
            Regex("/releases/tag/(v?\\d+)").find(loc)?.groupValues?.get(1)
        } finally {
            conn.disconnect()
        }
    }

    fun download(
        context: Context,
        apkUrl: String,
        onComplete: (Uri) -> Unit
    ): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Z GUNDAM OS 更新")
            setDescription("新しい版をダウンロード中...")
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                APK_NAME
            )
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
        }
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                runCatching { context.unregisterReceiver(this) }
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    if (statusCol >= 0 && cursor.getInt(statusCol) ==
                        DownloadManager.STATUS_SUCCESSFUL && uriCol >= 0
                    ) {
                        val local = Uri.parse(cursor.getString(uriCol))
                        onComplete(toInstallUri(context, local))
                    }
                }
                cursor.close()
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
        return downloadId
    }

    private fun toInstallUri(context: Context, local: Uri): Uri {
        if (local.scheme == "content") return local
        val path = local.path ?: return local
        val file = File(path)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun installApk(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }
}
