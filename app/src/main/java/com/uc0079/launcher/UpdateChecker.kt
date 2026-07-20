package com.uc0079.launcher

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer versionCode and downloads the APK.
 *
 * GitHub release convention used here:
 *   tag: "latest" (floating tag), asset: "gundam-os.apk"
 *   The release body or a dedicated asset is NOT used for versionCode; instead
 *   we embed the current versionCode into the release tag name via CI
 *   (e.g. "v2") so UpdateChecker can compare purely by tag sequence.
 *
 *   For this first implementation we use a simpler heuristic: fetch the
 *   releases/latest redirect and compare the resolved tag name against the
 *   tag of the version already installed.  If the repo's latest tag is newer
 *   than the installed one (lexicographic after stripping "v"), an update is
 *   available.  The CI workflow is updated to push a versioned tag in addition
 *   to "latest" so this works correctly.
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
                val tag = json.getString("tag_name")          // e.g. "v2"
                val apkUrl = json.getJSONArray("assets")
                    .let { arr ->
                        (0 until arr.length()).mapNotNull { arr.getJSONObject(it) }
                            .firstOrNull { it.getString("name") == APK_NAME }
                            ?.getString("browser_download_url")
                    } ?: return@runCatching null

                // Parse version number from tag (e.g. "v2" → 2)
                val remoteCode = tag.trimStart('v').toIntOrNull() ?: 1
                UpdateInfo(
                    available = remoteCode > currentVersionCode,
                    latestTag = tag,
                    apkUrl = apkUrl,
                    message = "v${currentVersionCode} → $tag"
                )
            }.getOrNull()
        }

    /**
     * Enqueues the APK download via DownloadManager and calls [onComplete]
     * with the file Uri when finished so MainActivity can trigger install.
     */
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
                        val uri = Uri.parse(cursor.getString(uriCol))
                        onComplete(uri)
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

    /** Triggers the system install dialog for a downloaded APK Uri. */
    fun installApk(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        runCatching { context.startActivity(intent) }
    }
}
