package com.uc0079.launcher

import java.io.InputStream
import java.nio.charset.Charset

/**
 * Parses Netscape Bookmark File Format HTML (Chrome / Firefox export)
 * into a flat list of title + URL. Folder hierarchy is ignored.
 */
object BookmarkHtmlImporter {

    data class Item(val title: String, val url: String)

    private val anchorRegex = Regex(
        """<a\s+[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun parse(html: String): List<Item> {
        val seen = LinkedHashMap<String, Item>()
        for (match in anchorRegex.findAll(html)) {
            val rawUrl = decodeHtml(match.groupValues[1].trim())
            if (rawUrl.isEmpty()) continue
            if (rawUrl.startsWith("javascript:", ignoreCase = true)) continue
            if (rawUrl.startsWith("data:", ignoreCase = true)) continue
            val normalized = LauncherViewModel.normalizeUrl(rawUrl) ?: continue
            if (seen.containsKey(normalized)) continue
            val title = stripTags(decodeHtml(match.groupValues[2]))
                .trim()
                .ifBlank { LauncherViewModel.hostLabel(normalized) }
            seen[normalized] = Item(title = title, url = normalized)
        }
        return seen.values.toList()
    }

    fun parse(stream: InputStream, charset: Charset = Charsets.UTF_8): List<Item> {
        val bytes = stream.readBytes()
        val text = tryDecode(bytes, charset)
        return parse(text)
    }

    private fun tryDecode(bytes: ByteArray, preferred: Charset): String {
        val asPreferred = String(bytes, preferred)
        // Chrome exports often declare charset in a meta tag; sniff if UTF-8 looks wrong.
        val meta = Regex(
            """charset\s*=\s*["']?([a-zA-Z0-9_\-]+)""",
            RegexOption.IGNORE_CASE
        ).find(asPreferred.take(2048))?.groupValues?.getOrNull(1)
        if (meta != null) {
            val named = runCatching { Charset.forName(meta) }.getOrNull()
            if (named != null && named != preferred) {
                return String(bytes, named)
            }
        }
        return asPreferred
    }

    private fun stripTags(s: String): String =
        s.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun decodeHtml(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("""&#(\d+);""")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
            }
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
            }
}
