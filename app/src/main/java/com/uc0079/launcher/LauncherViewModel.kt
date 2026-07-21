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

    /** packageName → custom display name (launcher-only). */
    var customLabels by mutableStateOf(loadCustomLabels())
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

    /** One-shot toast after browser Share → Z GUNDAM OS. */
    var shareHint by mutableStateOf<String?>(null)
        private set

    fun onHomeIntent() {
        homePulse++
    }

    fun consumeShareHint() {
        shareHint = null
    }

    /** Handle ACTION_SEND text/plain (browser Share). */
    fun handleSharedText(text: String?, subject: String?) {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) {
            shareHint = "URL が見つかりません"
            return
        }
        val normalized = normalizeUrl(raw)
        if (normalized == null) {
            shareHint = "有効な URL ではありません"
            return
        }
        val title = subject?.trim()?.takeIf { it.isNotBlank() } ?: hostLabel(normalized)
        shareHint = if (addWebFavorite(title, normalized)) {
            "お気に入りに追加しました"
        } else {
            "すでに登録済みです"
        }
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

            // Only prune uninstalled apps; keep web bookmarks.
            val prunedFav = favorites.filter { entry ->
                when (entry) {
                    is FavoriteEntry.App -> entry.packageName in installed
                    is FavoriteEntry.Web -> true
                }
            }
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

            val prunedLabels = customLabels.filterKeys { it in installed }
            if (prunedLabels.size != customLabels.size) {
                customLabels = prunedLabels
                saveCustomLabels()
            }

            loading = false
        }
    }

    fun displayLabel(app: AppInfo): String =
        customLabels[app.packageName]?.takeIf { it.isNotBlank() } ?: app.label

    fun displayLabel(pkg: String, fallback: String): String =
        customLabels[pkg]?.takeIf { it.isNotBlank() } ?: fallback

    fun setCustomLabel(pkg: String, name: String) {
        val trimmed = name.trim()
        customLabels = if (trimmed.isEmpty()) {
            customLabels - pkg
        } else {
            customLabels + (pkg to trimmed)
        }
        saveCustomLabels()
    }

    fun clearCustomLabel(pkg: String) {
        if (pkg !in customLabels) return
        customLabels = customLabels - pkg
        saveCustomLabels()
    }

    /** App favorites only (for places that need AppInfo). */
    val favoriteApps: List<AppInfo>
        get() {
            val byPkg = apps.associateBy { it.packageName }
            return favorites.mapNotNull { entry ->
                (entry as? FavoriteEntry.App)?.let { byPkg[it.packageName] }
            }
        }

    fun appForFavorite(entry: FavoriteEntry.App): AppInfo? =
        apps.firstOrNull { it.packageName == entry.packageName }

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

    fun isFavorite(pkg: String): Boolean =
        favorites.any { it is FavoriteEntry.App && it.packageName == pkg }

    fun toggleFavorite(pkg: String) {
        favorites = if (isFavorite(pkg)) {
            favorites.filterNot { it is FavoriteEntry.App && it.packageName == pkg }
        } else {
            favorites + FavoriteEntry.App(pkg)
        }
        saveFavorites()
    }

    /** Move any favorite (app or web) by list index. */
    fun moveFavoriteAt(index: Int, delta: Int) {
        if (index !in favorites.indices) return
        val j = (index + delta).coerceIn(0, favorites.lastIndex)
        if (index == j) return
        val mutable = favorites.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(j, item)
        favorites = mutable
        saveFavorites()
    }

    fun addWebFavorite(title: String, url: String): Boolean {
        val normalized = normalizeUrl(url) ?: return false
        // Avoid duplicates by URL.
        if (favorites.any { it is FavoriteEntry.Web && it.url == normalized }) return false
        val name = title.trim().ifBlank { hostLabel(normalized) }
        favorites = favorites + FavoriteEntry.Web(
            id = UUID.randomUUID().toString(),
            title = name,
            url = normalized
        )
        saveFavorites()
        return true
    }

    fun removeWebFavorite(id: String) {
        favorites = favorites.filterNot { it is FavoriteEntry.Web && it.id == id }
        saveFavorites()
    }

    fun renameWebFavorite(id: String, title: String) {
        val trimmed = title.trim().ifBlank { return }
        favorites = favorites.map { entry ->
            if (entry is FavoriteEntry.Web && entry.id == id) entry.copy(title = trimmed)
            else entry
        }
        saveFavorites()
    }

    fun openUrl(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun createFolder(name: String, initialPkg: String? = null): AppFolder {
        val folder = AppFolder(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "FOLDER" },
            packageNames = listOfNotNull(initialPkg)
        )
        val cleaned = if (initialPkg != null) {
            folders.map { f ->
                f.copy(packageNames = f.packageNames.filter { it != initialPkg })
            }.filter { it.packageNames.isNotEmpty() }
        } else {
            folders
        }
        folders = cleaned + folder
        if (initialPkg != null && isFavorite(initialPkg)) {
            favorites = favorites.filterNot {
                it is FavoriteEntry.App && it.packageName == initialPkg
            }
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
        folders = folders.map { f ->
            when {
                f.id == folderId && pkg !in f.packageNames ->
                    f.copy(packageNames = f.packageNames + pkg)
                f.id != folderId ->
                    f.copy(packageNames = f.packageNames.filter { it != pkg })
                else -> f
            }
        }.filter { it.packageNames.isNotEmpty() || it.id == folderId }

        if (isFavorite(pkg)) {
            favorites = favorites.filterNot {
                it is FavoriteEntry.App && it.packageName == pkg
            }
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

    private fun loadFavorites(): List<FavoriteEntry> {
        val raw = prefs.getString(KEY_FAV, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { FavoriteEntry.decode(it) }.toList()
    }

    private fun saveFavorites() {
        prefs.edit()
            .putString(KEY_FAV, favorites.joinToString("\n") { it.encode() })
            .apply()
    }

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

    private fun loadCustomLabels(): Map<String, String> {
        val raw = prefs.getString(KEY_LABELS, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        return raw.lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val pkg = line.substring(0, tab).trim()
            val name = line.substring(tab + 1).trim()
            if (pkg.isEmpty() || name.isEmpty()) null else pkg to name
        }.toMap()
    }

    private fun saveCustomLabels() {
        val encoded = customLabels.entries.joinToString("\n") { (pkg, name) ->
            "$pkg\t${name.replace('\n', ' ').replace('\t', ' ')}"
        }
        prefs.edit().putString(KEY_LABELS, encoded).apply()
    }

    companion object {
        private const val PREFS = "gundam_launcher"
        private const val KEY_FAV = "favorites"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_LABELS = "custom_labels"

        fun normalizeUrl(raw: String): String? {
            var s = raw.trim()
            if (s.isEmpty()) return null
            // Prefer first http(s) URL if text contains more than a URL.
            val match = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
                .find(s)
            if (match != null) {
                s = match.value.trimEnd('.', ',', ';', ')', ']')
            } else if (!s.contains("://")) {
                if (!s.contains('.') || s.contains(' ')) return null
                s = "https://$s"
            }
            val uri = runCatching { Uri.parse(s) }.getOrNull() ?: return null
            if (uri.scheme !in listOf("http", "https")) return null
            if (uri.host.isNullOrBlank()) return null
            return s
        }

        fun hostLabel(url: String): String =
            runCatching { Uri.parse(url).host?.removePrefix("www.") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "WEB"
    }
}
