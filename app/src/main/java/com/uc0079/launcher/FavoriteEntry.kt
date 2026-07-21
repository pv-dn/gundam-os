package com.uc0079.launcher

/**
 * Ordered favorite entry: either an installed app or a web bookmark.
 *
 * Prefs encoding (one line each):
 * - App (legacy-compatible): `com.example.app`
 * - Web: `web|<id>|<title>|<url>`
 */
sealed class FavoriteEntry {
    data class App(val packageName: String) : FavoriteEntry()
    data class Web(val id: String, val title: String, val url: String) : FavoriteEntry()

    fun encode(): String = when (this) {
        is App -> packageName
        is Web -> {
            val safeTitle = title.replace('|', ' ').replace('\n', ' ')
            val safeUrl = url.replace('|', ' ').replace('\n', ' ')
            "web|$id|$safeTitle|$safeUrl"
        }
    }

    companion object {
        fun decode(line: String): FavoriteEntry? {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.startsWith("web|")) {
                val parts = trimmed.split("|", limit = 4)
                if (parts.size < 4) return null
                val id = parts[1].ifBlank { return null }
                val title = parts[2].ifBlank { "WEB" }
                val url = parts[3].trim().ifBlank { return null }
                return Web(id = id, title = title, url = url)
            }
            return App(trimmed)
        }
    }
}
