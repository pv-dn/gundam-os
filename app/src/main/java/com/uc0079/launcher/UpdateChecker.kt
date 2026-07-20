package com.uc0079.launcher

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer versionCode and downloads the APK.
 *
 * Release tag convention: "v{versionCode}" (e.g. "v4").
 */
object UpdateChecker {

    private const val OWNER = "pv-dn"
    private const val REPO = "gundam-os"
    private const val APK_NAME = "gundam-os.apk"
    private const val API =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    data class UpdateInfo(
        val available: Boolean,
        val latestTag: String = "",
        val apkUrl: String = "",
        val message: String = ""
    )

    /** Run on IO dispatcher. Returns null on network failure. */
    suspend fun check(currentVersionCode: Int): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(body)
                val tag = json.getString("tag_name")
                val apkUrl = json.getJSONArray("assets")
                    .let { arr ->
                        (0 until arr.length()).mapNotNull { arr.getJSONObject(it) }
                            .firstOrNull { it.getString("name") == APK_NAME }
                            ?.getString("browser_download_url")
                    } ?: return@runCatching null

                val remoteCode = tag.trimStart('v').toIntOrNull() ?: 1
                UpdateInfo(
                    available = remoteCode > currentVersionCode,
                    latestTag = tag,
                    apkUrl = apkUrl,
                    message = "v${currentVersionCode} → $tag"
                )
            }.getOrNull()
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

    /** Convert file:// download URI to a shareable content:// URI via FileProvider. */
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

    /** Triggers the system install dialog for a downloaded APK Uri. */
    fun installApk(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }
}
