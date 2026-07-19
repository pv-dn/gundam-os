package com.uc0079.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    var favorites by mutableStateOf(loadFavorites())
        private set

    var loading by mutableStateOf(true)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            val loaded = withContext(Dispatchers.IO) {
                AppRepository.loadApps(getApplication())
            }
            apps = loaded
            val installed = loaded.mapTo(HashSet()) { it.packageName }
            val pruned = favorites.filter { it in installed }
            if (pruned.size != favorites.size) {
                favorites = pruned
                saveFavorites()
            }
            loading = false
        }
    }

    val favoriteApps: List<AppInfo>
        get() {
            val byPkg = apps.associateBy { it.packageName }
            return favorites.mapNotNull { byPkg[it] }
        }

    fun isFavorite(pkg: String): Boolean = pkg in favorites

    fun toggleFavorite(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        saveFavorites()
    }

    fun launchApp(pkg: String) {
        val pm = getApplication<Application>().packageManager
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun openAppInfo(pkg: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun uninstall(pkg: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    private fun loadFavorites(): List<String> {
        val raw = prefs.getString(KEY_FAV, "").orEmpty()
        return if (raw.isBlank()) emptyList()
        else raw.split("\n").filter { it.isNotBlank() }
    }

    private fun saveFavorites() {
        prefs.edit().putString(KEY_FAV, favorites.joinToString("\n")).apply()
    }

    companion object {
        private const val PREFS = "gundam_launcher"
        private const val KEY_FAV = "favorites"
    }
}
