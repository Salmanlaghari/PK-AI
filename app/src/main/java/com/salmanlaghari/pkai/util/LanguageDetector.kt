package com.salmanlaghari.pkai.util

/**
 * Detects the user's language so the AI can reply in the SAME language.
 *
 * Supports:
 * - Urdu script (اردو) — Arabic Unicode block
 * - Roman Urdu (e.g. "aap kaise ho") — common Roman-Urdu markers
 * - English (default)
 */
object LanguageDetector {

    enum class Lang(val instruction: String) {
        URDU_SCRIPT(
            "The user wrote in Urdu script (اردو). Reply ENTIRELY in Urdu script. " +
            "Use natural, fluent Urdu."
        ),
        ROMAN_URDU(
            "The user wrote in Roman Urdu (Urdu written in Latin letters). Reply ENTIRELY in Roman Urdu " +
            "— the same casual Latin-script Urdu style the user uses (e.g. 'aap', 'kaise', 'bohat', 'shukriya'). " +
            "Do NOT reply in English and do NOT use Urdu script."
        ),
        ENGLISH(
            "The user wrote in English. Reply ENTIRELY in clear, natural English."
        )
    }

    // Common Roman-Urdu words — strong signal even in short messages
    private val ROMAN_URDU_MARKERS = setOf(
        "aap", "aapko", "tum", "tumhe", "tumhein", "mein", "main", "mera", "meri", "mere",
        "tera", "teri", "tere", "uska", "uski", "uske", "iska", "iski", "iske",
        "kaise", "kaisa", "kaisi", "kya", "kyun", "kyu", "kab", "kahan", "kaun",
        "bohat", "bahut", "zyada", "kam", "nahi", "nahin", "hai", "hain", "ho", "hun",
        "tha", "thi", "the", "kar", "karo", "karein", "karna", "kiya", "kiye",
        "shukriya", "meherbani", "khuda", "allah", "bhai", "yaar", "acha", "accha",
        "theek", "sahi", "galat", "pata", "maloom", "samajh", "batao", "bata",
        "suno", "dekho", "jao", "aao", "rakho", "likho", "bhejo", "khol", "band",
        "gaana", "gana", "geet", "sunao", "acha", "maza", "maza", "waah", "wah"
    )

    fun detect(text: String): Lang {
        val t = text.trim()
        if (t.isEmpty()) return Lang.ENGLISH

        // Urdu/Arabic script block
        if (t.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\uFB50'..'\uFDFF' }) {
            return Lang.URDU_SCRIPT
        }

        // Roman Urdu: count marker words
        val words = t.lowercase().split(Regex("[^a-zA-Z]+")).filter { it.length >= 2 }
        if (words.isEmpty()) return Lang.ENGLISH
        val hits = words.count { it in ROMAN_URDU_MARKERS }
        // 1 strong marker in a short message, or 2+ in longer ones
        if (hits >= 2 || (hits == 1 && words.size <= 6)) {
            return Lang.ROMAN_URDU
        }
        return Lang.ENGLISH
    }
}
