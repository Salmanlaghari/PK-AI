package com.salmanlaghari.pkai.util

/**
 * STRICT client-side content-safety filter for AI image-generation prompts.
 *
 * This is the first line of defense for Google Play's Sexual Content policy:
 * it runs BEFORE any network call is made (see
 * [com.salmanlaghari.pkai.data.repository.PollinationsImageRepository.generateImage]),
 * so a blocked prompt never reaches the image service. A second layer —
 * `safe=true` on the Pollinations request URL — asks the server to reject
 * anything that slips through.
 *
 * How matching works:
 * 1. **Normalize**: lowercase the prompt and de-obfuscate leetspeak
 *    (0→o, 1→l, 3→e, 4→a, 5→s, 7→t, 8→b).
 * 2. **Tokenize** on non-alphanumeric separators and also build a **compact**
 *    form with all separators stripped — so "n.u.d.e", "n_u_d_e" and
 *    "n u d e" all collapse to "nude".
 * 3. **Match** a blocklisted term when it is either an exact token
 *    (word-boundary match) or appears in the compact form in a way that is
 *    NOT buried inside a single longer innocent word. The latter rule is the
 *    Scunthorpe guard: "classic" contains "ass" but is one longer token, so
 *    it is NOT blocked, while "n.u.d.e" spans four tokens and IS blocked.
 */
object ContentSafetyFilter {

    private val LEET_SPEAK: Map<Char, Char> = mapOf(
        '0' to 'o',
        '1' to 'l',
        '3' to 'e',
        '4' to 'a',
        '5' to 's',
        '7' to 't',
        '8' to 'b'
    )

    /**
     * Comprehensive blocklist of sexual / explicit / nude-related terms.
     * Entries are normalized with [normalize] at startup, so plain lowercase
     * spellings here cover leetspeak variants in user input.
     */
    private val BLOCKED_TERMS: Set<String> = setOf(
        // Nudity / undress
        "nude", "nudes", "nudity", "naked", "undressed", "unclothed",
        "topless", "bottomless", "strip", "strips", "stripped", "stripping",
        "stripper", "strippers", "striptease", "stripclub",
        // Sex / porn
        "sex", "sexy", "sexual", "sexually", "sexuality",
        "erotic", "erotica", "erotically",
        "porn", "porno", "pornos", "pornographic", "pornography",
        "pornstar", "pornstars", "xxx", "hentai", "nsfw",
        "explicit",
        // Female anatomy (slang + clinical, both abused in prompts)
        "breast", "breasts", "boob", "boobs", "boobie", "boobies",
        "tit", "tits", "titties", "nipple", "nipples", "areola", "areolas",
        "vagina", "vaginas", "pussy", "clit", "clitoris", "labia", "vulva",
        "cunt", "cunts",
        // Male anatomy
        "penis", "penises", "dick", "dicks", "cock", "cocks",
        "prick", "phallus", "testicle", "testicles", "scrotum",
        // Buttocks / anal
        "ass", "asses", "booty", "butt", "butts", "buttocks",
        "anal", "anus",
        // Sexual acts
        "orgasm", "orgasms", "climax",
        "masturbate", "masturbates", "masturbating", "masturbation",
        "ejaculate", "ejaculation", "cum", "cumming", "cumshot", "creampie",
        "blowjob", "blowjobs", "handjob", "handjobs", "rimjob",
        "deepthroat", "gangbang", "threesome", "threesomes",
        "orgy", "orgies", "bukkake", "foreplay", "makeout", "sexting",
        "cybersex", "jizz", "boner",
        // Fetish / BDSM
        "fetish", "fetishes", "fetishist", "bdsm", "bondage",
        "domination", "dominate", "submissive",
        "sadism", "masochism", "sadist", "masochist",
        "kink", "kinky", "spank", "spanking",
        // Suggestive clothing / framing
        "lingerie", "thong", "thongs", "gstring", "bikini", "bikinis",
        "panties", "upskirt", "downblouse",
        // Sex work
        "escort", "escorts", "prostitute", "prostitutes", "prostitution",
        "whore", "whores", "slut", "sluts", "slutty",
        "hooker", "hookers", "onlyfans", "playboy",
        "camgirl", "camgirls", "brothel", "swinger", "swingers",
        // Arousal / seduction
        "horny", "aroused", "arousal", "lust", "lustful", "lusty",
        "seduce", "seduces", "seduction", "seductive",
        "sensual", "sensuality", "sensuous",
        // Intercourse
        "intercourse", "coitus", "fornication", "sodomy",
        // Sex toys / dolls
        "dildo", "dildos", "vibrator", "vibrators",
        "sextoy", "sextoys", "sexdoll", "fleshlight",
        // Non-consensual / abusive — must never be generatable
        "rape", "rapes", "rapist", "rapists",
        "molestation", "molester", "molesters",
        "pedophile", "pedophiles", "pedophilia",
        "bestiality", "incest", "incestuous", "necrophilia",
        // Misc explicit slang
        "milf", "dilf"
    ).map { normalize(it) }.filter { it.isNotEmpty() }.toSet()

    /** Lowercase + leetspeak de-obfuscation. */
    internal fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (raw in text.lowercase()) {
            sb.append(LEET_SPEAK[raw] ?: raw)
        }
        return sb.toString()
    }

    private data class Tokenization(
        val tokens: List<String>,
        val compact: String,
        /** [start, end) spans of each token inside [compact]. */
        val spans: List<Pair<Int, Int>>
    )

    private fun tokenize(prompt: String): Tokenization {
        val normalized = normalize(prompt)
        val tokens = mutableListOf<String>()
        val spans = mutableListOf<Pair<Int, Int>>()
        val compact = StringBuilder()
        var i = 0
        while (i < normalized.length) {
            val c = normalized[i]
            if (c.isLetterOrDigit()) {
                val start = compact.length
                while (i < normalized.length && normalized[i].isLetterOrDigit()) {
                    compact.append(normalized[i])
                    i++
                }
                tokens.add(compact.substring(start, compact.length))
                spans.add(start to compact.length)
            } else {
                i++
            }
        }
        return Tokenization(tokens, compact.toString(), spans)
    }

    /**
     * Returns the first blocklisted term matched in [prompt], or null when the
     * prompt is clean. Matching is word-boundary-aware: a term buried inside a
     * single longer token (e.g. "ass" in "classic") does NOT match, while the
     * same letters split across separators (e.g. "n.u.d.e") DO match.
     */
    fun matchedTerm(prompt: String): String? {
        val t = tokenize(prompt)
        if (t.tokens.isEmpty()) return null
        for (term in BLOCKED_TERMS) {
            // (a) Exact token match — the word-boundary case.
            if (term in t.tokens) return term
            // (b) Separator-stripped match — catches "n.u.d.e" / "n_u_d_e" /
            // "n u d e", but skips occurrences fully inside one longer token
            // (the Scunthorpe guard: "classic", "shitake", "document"...).
            var idx = t.compact.indexOf(term)
            while (idx >= 0) {
                val end = idx + term.length
                val buriedInLongerToken = t.spans.any { (s, e) ->
                    s <= idx && end <= e && (e - s) > term.length
                }
                if (!buriedInLongerToken) return term
                idx = t.compact.indexOf(term, idx + 1)
            }
        }
        return null
    }

    /** True when [prompt] must be refused without any network call. */
    fun isBlocked(prompt: String): Boolean = matchedTerm(prompt) != null

    /**
     * Human-readable reason for a blocked prompt, or null when clean.
     * Suitable for logging; the user-facing copy lives at the call site.
     */
    fun blockedReason(prompt: String): String? =
        matchedTerm(prompt)?.let { "blocked term \"$it\"" }
}
