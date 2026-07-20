package com.uc0079.launcher

import android.os.Build
import java.text.Normalizer

/**
 * Maps an app label to an A–Z (or '#') bucket using romaji of the first sound.
 * Hiragana / katakana use a gojuon table; kanji uses ICU Latin when available.
 */
object IndexLetter {

    fun of(label: String): Char {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return '#'

        // Skip decorative prefixes like 【】『』etc.
        val start = trimmed.indexOfFirst { !isIgnorablePrefix(it) }
            .let { if (it < 0) 0 else it }
        val text = trimmed.substring(start)
        if (text.isEmpty()) return '#'

        latinLetter(text[0])?.let { return it }

        kanaLetter(text)?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            icuLatinLetter(text)?.let { return it }
        }

        return '#'
    }

    private fun isIgnorablePrefix(c: Char): Boolean =
        c.isWhitespace() ||
            c in "「」『』【】（）()[]<>《》〈〉\"'・ｰ-–—"

    private fun latinLetter(c: Char): Char? = when {
        c in 'A'..'Z' -> c
        c in 'a'..'z' -> c.uppercaseChar()
        c in 'Ａ'..'Ｚ' -> ('A' + (c - 'Ａ'))
        c in 'ａ'..'ｚ' -> ('A' + (c - 'ａ'))
        else -> null
    }

    /** First romaji letter of leading hiragana/katakana (including youon). */
    private fun kanaLetter(text: String): Char? {
        val c0 = toHiragana(text[0])
        if (c0 !in 'ぁ'..'ん' && c0 != 'ゔ') return null

        // Youon: きゃ / しゃ / ちゃ …
        if (text.length >= 2) {
            val c1 = toHiragana(text[1])
            if (c1 in YOUON_SMALL) {
                YOUON[c0]?.let { return it }
            }
        }
        return HIRA_LETTER[c0] ?: '#'
    }

    private fun toHiragana(c: Char): Char = when (c) {
        in 'ァ'..'ン' -> (c - 'ァ' + 'ぁ')
        'ヴ' -> 'ゔ'
        'ヵ' -> 'か'
        'ヶ' -> 'け'
        else -> c
    }

    private fun icuLatinLetter(text: String): Char? {
        return runCatching {
            val t = android.icu.text.Transliterator
                .getInstance("Any-Latin; Latin-ASCII")
            val latin = Normalizer.normalize(
                t.transliterate(text),
                Normalizer.Form.NFD
            )
            latin.firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }
                ?.uppercaseChar()
        }.getOrNull()
    }

    private val YOUON_SMALL = setOf('ゃ', 'ゅ', 'ょ', 'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ')

    private val YOUON = mapOf(
        'き' to 'K', 'ぎ' to 'G',
        'し' to 'S', 'じ' to 'J',
        'ち' to 'C', 'ぢ' to 'J',
        'に' to 'N',
        'ひ' to 'H', 'び' to 'B', 'ぴ' to 'P',
        'み' to 'M',
        'り' to 'R',
    )

    private val HIRA_LETTER: Map<Char, Char> = buildMap {
        fun row(letter: Char, chars: String) = chars.forEach { put(it, letter) }
        row('A', "あいうえおぁぃぅぇぉ")
        row('K', "かきくけこ")
        row('G', "がぎぐげご")
        row('S', "さしすせそ")
        row('Z', "ざじずぜぞ")
        row('T', "たちつてとっ")
        row('D', "だぢづでど")
        row('N', "なにぬねのん")
        row('H', "はひふへほ")
        row('B', "ばびぶべぼ")
        row('P', "ぱぴぷぺぽ")
        row('M', "まみむめも")
        row('Y', "やゆよゃゅょ")
        row('R', "らりるれろ")
        row('W', "わゐゑを")
        put('ゔ', 'V')
        // Vowels written alone already covered; long vowel mark ignored via prefix skip
    }
}
