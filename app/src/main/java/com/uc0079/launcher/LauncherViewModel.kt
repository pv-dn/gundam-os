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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    var favorites by mutableStateOf(loadFavorites())
        private set

    var folders by mutableStateOf(loadFolders())
        private set

    var loading by mutableStateOf(true)
        private set

    /** Non-null when a new version is available for download. */
    var updateInfo by mutableStateOf<UpdateChecker.UpdateInfo?>(null)
        private set

    /**
     * Incremented when the system Home intent arrives while we're already open
     * (e.g. user pressed Home on the all-apps screen). UI watches this to reset.
     */
    var homePulse by mutableStateOf(0)
        private set

    fun onHomeIntent() {
        homePulse++
    }

    init {
        refresh()
        checkForUpdate()
    }

    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            if (!force) delay(2_000)
            val app = getApplication<Application>()
            val versionCode = runCatching {
                UpdateChecker.currentVersionCode(app)
            }.getOrDefault(1)
            val info = UpdateChecker.check(versionCode) ?: return@launch
            if (info.available) updateInfo = info
        }
    }

    fun dismissUpdate() {
        updateInfo = null
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            val loaded = withContext(Dispatchers.IO) {
                AppRepository.loadApps(getApplication())
            }
            apps = loaded
            val installed = loaded.mapTo(HashSet()) { it.packageName }

            val prunedFav = favorites.filter { it in installed }
            if (prunedFav.size != favorites.size) {
                favorites = prunedFav
                saveFavorites()
            }

            val prunedFolders = folders.map { folder ->
                folder.copy(packageNames = folder.packageNames.filter { it in installed })
            }
            if (prunedFolders != folders) {
                folders = prunedFolders
                saveFolders()
            }

            loading = false
        }
    }

    val favoriteApps: List<AppInfo>
        get() {
            val byPkg = apps.associateBy { it.packageName }
            return favorites.mapNotNull { byPkg[it] }
        }

    /** Packages currently stored inside any folder (hidden from A–Z). */
    val shelvedPackages: Set<String>
        get() = folders.flatMapTo(HashSet()) { it.packageNames }

    fun appsInFolder(folderId: String): List<AppInfo> {
        val folder = folders.firstOrNull { it.id == folderId } ?: return emptyList()
        val byPkg = apps.associateBy { it.packageName }
        return folder.packageNames.mapNotNull { byPkg[it] }
    }

    fun folderOf(pkg: String): AppFolder? =
        folders.firstOrNull { pkg in it.packageNames }

    fun isFavorite(pkg: String): Boolean = pkg in favorites

    fun toggleFavorite(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        saveFavorites()
    }

    fun createFolder(name: String, initialPkg: String? = null): AppFolder {
        val folder = AppFolder(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "FOLDER" },
            packageNames = listOfNotNull(initialPkg)
        )
        // If the app was already in another folder, move it out first.
        val cleaned = if (initialPkg != null) {
            folders.map { f ->
                f.copy(packageNames = f.packageNames.filter { it != initialPkg })
            }.filter { it.packageNames.isNotEmpty() }
        } else {
            folders
        }
        folders = cleaned + folder
        // Putting in a folder removes from home favorites (it's "stored away").
        if (initialPkg != null && initialPkg in favorites) {
            favorites = favorites - initialPkg
            saveFavorites()
        }
        saveFolders()
        return folder
    }

    fun renameFolder(folderId: String, name: String) {
        val trimmed = name.trim().ifBlank { return }
        folders = folders.map {
            if (it.id == folderId) it.copy(name = trimmed) else it
        }
        saveFolders()
    }

    fun deleteFolder(folderId: String) {
        folders = folders.filter { it.id != folderId }
        saveFolders()
    }

    fun addToFolder(folderId: String, pkg: String) {
        // Remove from any other folder first (one folder per app).
        folders = folders.map { f ->
            when {
                f.id == folderId && pkg !in f.packageNames ->
                    f.copy(packageNames = f.packageNames + pkg)
                f.id != folderId ->
                    f.copy(packageNames = f.packageNames.filter { it != pkg })
                else -> f
            }
        }.filter { it.packageNames.isNotEmpty() || it.id == folderId }

        if (pkg in favorites) {
            favorites = favorites - pkg
            saveFavorites()
        }
        saveFolders()
    }

    fun removeFromFolder(folderId: String, pkg: String) {
        folders = folders.map { f ->
            if (f.id == folderId) f.copy(packageNames = f.packageNames.filter { it != pkg })
            else f
        }
        saveFolders()
    }

    fun openHomeAppSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { getApplication<Application>().startActivity(intent) }
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

    /**
     * One folder per line: id|name|pkg1,pkg2
     * Pipes in names are replaced with spaces on save.
     */
    private fun loadFolders(): List<AppFolder> {
        val raw = prefs.getString(KEY_FOLDERS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size < 2) return@mapNotNull null
            val id = parts[0].ifBlank { return@mapNotNull null }
            val name = parts[1].ifBlank { "FOLDER" }
            val pkgs = if (parts.size >= 3 && parts[2].isNotBlank()) {
                parts[2].split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else emptyList()
            AppFolder(id = id, name = name, packageNames = pkgs)
        }.toList()
    }

    private fun saveFolders() {
        val encoded = folders.joinToString("\n") { f ->
            val safeName = f.name.replace('|', ' ').replace('\n', ' ')
            val pkgs = f.packageNames.joinToString(",")
            "${f.id}|$safeName|$pkgs"
        }
        prefs.edit().putString(KEY_FOLDERS, encoded).apply()
    }

    companion object {
        private const val PREFS = "gundam_launcher"
        private const val KEY_FAV = "favorites"
        private const val KEY_FOLDERS = "folders"
    }
}
