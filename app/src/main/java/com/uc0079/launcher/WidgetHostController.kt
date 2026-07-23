package com.uc0079.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Minimal but working AppWidget host: lets the user pick an installed widget via the
 * system picker, binds it, runs its configuration activity if needed, and keeps the
 * placed widget ids so they can be rendered on the home screen.
 */
class WidgetHostController(private val activity: ComponentActivity) {

    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val host = AppWidgetHost(activity.applicationContext, HOST_ID)
    private val manager = AppWidgetManager.getInstance(activity.applicationContext)

    val widgetIds: SnapshotStateList<Int> = mutableStateListOf<Int>().also {
        it.addAll(loadIds())
    }

    private var pendingConfigureId: Int = INVALID

    private val pickLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, INVALID) ?: INVALID
            if (result.resultCode == Activity.RESULT_OK && id != INVALID) {
                afterBind(id)
            } else if (id != INVALID) {
                runCatching { host.deleteAppWidgetId(id) }
            }
        }

    private val configureLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingConfigureId
            pendingConfigureId = INVALID
            if (result.resultCode == Activity.RESULT_OK && id != INVALID) {
                commit(id)
            } else if (id != INVALID) {
                runCatching { host.deleteAppWidgetId(id) }
            }
        }

    fun start() {
        runCatching { host.startListening() }
    }

    fun stop() {
        runCatching { host.stopListening() }
    }

    fun pickWidget() {
        val id = host.allocateAppWidgetId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList())
        }
        runCatching { pickLauncher.launch(intent) }.onFailure {
            runCatching { host.deleteAppWidgetId(id) }
        }
    }

    private fun afterBind(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        val configure = info?.configure
        if (configure != null) {
            pendingConfigureId = id
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            val launched = runCatching { configureLauncher.launch(intent) }.isSuccess
            if (!launched) {
                pendingConfigureId = INVALID
                commit(id)
            }
        } else {
            commit(id)
        }
    }

    private fun commit(id: Int) {
        if (id !in widgetIds) {
            widgetIds.add(id)
            saveIds()
        }
    }

    fun removeWidget(id: Int) {
        if (isPinnedWidget(id)) return
        widgetIds.remove(id)
        runCatching { host.deleteAppWidgetId(id) }
        saveIds()
    }

    fun createHostView(context: Context, id: Int): AppWidgetHostView? {
        val info: AppWidgetProviderInfo = manager.getAppWidgetInfo(id) ?: return null
        return runCatching { host.createView(context, id, info) }.getOrNull()
    }

    /** Minimum height reported by the widget, in px (already density-scaled by the framework). */
    fun minHeightPx(id: Int): Int = manager.getAppWidgetInfo(id)?.minHeight ?: 0

    /** Google Search bar — kept on home; no remove affordance. */
    fun isPinnedWidget(id: Int): Boolean {
        val info = manager.getAppWidgetInfo(id) ?: return false
        val pkg = info.provider?.packageName.orEmpty()
        val cls = info.provider?.className.orEmpty()
        if (pkg == GOOGLE_SEARCH_PKG) {
            // Prefer search-bar providers; fall back to any widget from that package
            // if class naming differs by OEM / Play version.
            if (cls.contains("SearchWidget", ignoreCase = true) ||
                cls.contains("SearchBar", ignoreCase = true) ||
                cls.contains("GoogleSearch", ignoreCase = true)
            ) {
                return true
            }
            // Single-widget installs are almost always the search bar.
            val fromPkg = manager.getInstalledProviders()
                .count { it.provider?.packageName == GOOGLE_SEARCH_PKG }
            if (fromPkg <= 2) return true
        }
        return false
    }

    private fun loadIds(): List<Int> {
        val raw = prefs.getString(KEY, "").orEmpty()
        return if (raw.isBlank()) emptyList()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun saveIds() {
        prefs.edit().putString(KEY, widgetIds.joinToString(",")).apply()
    }

    companion object {
        private const val PREFS = "gundam_launcher"
        private const val KEY = "widgets"
        private const val HOST_ID = 0x47554E44 // "GUND"
        private const val INVALID = -1
        private const val GOOGLE_SEARCH_PKG = "com.google.android.googlequicksearchbox"
    }
}
