package com.salmanlaghari.pkai.util

import java.text.Normalizer

/**
 * Best-effort client-side text prefilter for sexual and profane content.
 *
 * This lexical check is defense in depth only: it cannot recognize every
 * euphemism, language, obfuscation, or semantic meaning and is not a substitute
 * for provider-side moderation. Image generation is disabled until the app has
 * a reliable way to moderate generated image outputs.
 */
object ContentSafetyFilter {

    const val BLOCKED_OUTPUT_MESSAGE: String =
        "I can't display sexual or profane content. Please try a different request."

    private val LEET_SPEAK: Map<Char, Char> = mapOf(
        '0' to 'o', '1' to 'l', '3' to 'e', '4' to 'a',
        '5' to 's', '7' to 't', '8' to 'b', '@' to 'a', '$' to 's'
    )

    /** Normalized English and common Roman Urdu terms; deliberately not exhaustive. */
    private val BLOCKED_TERMS: Set<String> = setOf(
        // Sexual content, nudity, sexualized clothing and acts.
        "nude", "nudes", "nudity", "naked", "undressed", "unclothed",
        "topless", "bottomless", "strip", "stripped", "stripping", "stripper",
        "sex", "sexy", "sexual", "sexually", "erotic", "erotica", "porn",
        "porno", "pornographic", "pornography", "pornstar", "xxx", "hentai",
        "nsfw", "explicit", "breast", "breasts", "boob", "boobs", "tit", "tits",
        "nipple", "nipples", "areola", "vagina", "pussy", "clit", "clitoris",
        "labia", "vulva", "cunt", "penis", "dick", "cock", "prick", "phallus",
        "testicle", "scrotum", "ass", "asses", "booty", "buttocks", "anal", "anus",
        "orgasm", "masturbate", "masturbation", "ejaculate", "cum", "cumshot",
        "creampie", "blowjob", "handjob", "deepthroat", "gangbang", "threesome",
        "orgy", "bukkake", "foreplay", "sexting", "cybersex", "jizz", "boner",
        "fetish", "bdsm", "bondage", "domination", "submissive", "sadism",
        "masochism", "kinky", "spanking", "lingerie", "thong", "gstring", "bikini",
        "panties", "upskirt", "downblouse", "escort", "prostitute", "prostitution",
        "whore", "slut", "hooker", "onlyfans", "playboy", "camgirl", "brothel",
        "swinger", "horny", "aroused", "lustful", "seduce", "seduction", "seductive",
        "sensual", "intercourse", "coitus", "fornication", "sodomy", "dildo", "vibrator",
        "sextoy", "sexdoll", "fleshlight", "rape", "rapist", "molestation", "molester",
        "pedophile", "pedophilia", "bestiality", "incest", "necrophilia", "milf", "dilf",
        // Profanity, including common Roman Urdu spellings.
        "fuck", "fucking", "fucked", "fucker", "motherfucker", "shit", "bullshit",
        "bitch", "bitches", "asshole", "bastard", "damn", "goddamn", "hell", "crap",
        "piss", "pissed", "wtf", "stfu", "faggot", "madarchod", "maderchod",
        "behenchod", "bhenchod", "chutiya", "chutia", "chut", "gaand", "gand",
        "lund", "lora", "randi", "harami", "kamina", "kameena", "bhosdike", "bhosdi",
        "bhosda", "chudai", "chod", "choda", "chodi", "nanga", "nangi", "nangay"
    ).map { normalize(it) }.filter { it.isNotEmpty() }.toSet()

    private data class Tokenization(
        val tokens: List<String>,
        val compact: String,
        val spans: List<Pair<Int, Int>>
    )

    /** Lowercase, remove combining marks and map common leetspeak characters. */
    internal fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (raw in decomposed) {
            if (Character.getType(raw) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(LEET_SPEAK[raw] ?: raw)
        }
        return sb.toString()
    }

    private fun tokenize(text: String): Tokenization {
        val normalized = normalize(text)
        val tokens = mutableListOf<String>()
        val spans = mutableListOf<Pair<Int, Int>>()
        val compact = StringBuilder()
        var index = 0
        while (index < normalized.length) {
            if (normalized[index].isLetterOrDigit()) {
                val start = compact.length
                while (index < normalized.length && normalized[index].isLetterOrDigit()) {
                    compact.append(normalized[index])
                    index++
                }
                tokens.add(compact.substring(start, compact.length))
                spans.add(start to compact.length)
            } else {
                index++
            }
        }
        return Tokenization(tokens, compact.toString(), spans)
    }

    /** Returns a matched unsafe term, if any. Longer innocent words are not substring-matched. */
    fun matchedTerm(text: String): String? {
        val tokenization = tokenize(text)
        if (tokenization.tokens.isEmpty()) return null
        for (term in BLOCKED_TERMS) {
            if (term in tokenization.tokens) return term
            var index = tokenization.compact.indexOf(term)
            while (index >= 0) {
                val end = index + term.length
                val buriedInLongerToken = tokenization.spans.any { (start, stop) ->
                    start <= index && end <= stop && (stop - start) > term.length
                }
                if (!buriedInLongerToken) return term
                index = tokenization.compact.indexOf(term, index + 1)
            }
        }
        return null
    }

    /** Whether input or output text matches the conservative lexical blocklist. */
    fun isBlocked(text: String): Boolean = matchedTerm(text) != null

    /** Replaces unsafe model text before it is rendered, saved or spoken. */
    fun filterAiOutput(text: String): String =
        if (isBlocked(text)) BLOCKED_OUTPUT_MESSAGE else text

    /** Safe to log: returns only the matched category term, never the full prompt. */
    fun blockedReason(text: String): String? =
        matchedTerm(text)?.let { "blocked term \"$it\"" }
}
