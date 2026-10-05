package com.salmanlaghari.pkai.util

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Song search via PagalWorld's WordPress API (same source as Pulse Music Player).
 *
 * [searchSong] takes a song name, finds the best match, and returns full
 * metadata: title, artist, artwork URL, and a streamable audio URL.
 */
object SongSearchHelper {

    private const val TAG = "SongSearch"
    private const val BASE = "https://pagal-world.com.co"
    private const val TIMEOUT = 8000
    /** Max chars read from any HTTP response (OOM guard). */
    private const val MAX_RESPONSE_CHARS = 512 * 1024
    /**
     * Test-only hook: when set, [searchSong] returns this lambda's result
     * instead of touching the network. Null (default) means real lookup.
     * Scoped to tests — production code never sets this.
     */
    @Volatile
    var testSearchOverride: (suspend (String) -> SongResult?)? = null
    private val PLAY_PREFIX = Regex("(?i)^play\\s+(.+)$")
    private val PLAY_SUFFIX = Regex("(?i)^(.+?)\\s+play\\s+(karo|kar)\\s*$")
    private val SONG_ACTION = Regex("(?i)^(.+?)\\s+(song|gana|gaana)\\s+(sunao|suna|play|chalao|lagao)\\s*$")
    private val SONG_PREFIX = Regex("(?i)^(song|gana|gaana)\\s*:\\s*(.+)$")
    private val SUNAO = Regex("(?i)^([^\\s]+)\\s+sunao\\s*$")
    // "search X song [karo]" — explicit search request (Prince's feedback: plain
    // "Search Hum Dil de chuke Sanam song" must open a song card, not text).
    private val SEARCH_PREFIX = Regex("(?i)^(search|find|dhundo|dhundho|dhundoo|dhoondo|talash)\\s+(.+?)\\s+(song|gana|gaana)\\s*(karo|kar|karein)?\\s*$")
    // "X song search|find|…|talash [karo]" — explicit search request, suffix form.
    private val SEARCH_SUFFIX = Regex("(?i)^(.+?)\\s+(song|gana|gaana)\\s+(search|find|dhundo|dhundho|dhundoo|dhoondo|talash)\\s*(karo|kar|karein)?\\s*$")
    // "X song/gana/gaana" — bare title + song suffix (Prince's on-device
    // feedback: "Sanam Teri qasem song" must open a song card, not text).
    // Guarded by looksLikeSongTitle to avoid hijacking normal chat.
    private val SONG_SUFFIX_BARE = Regex("(?i)^(.+?)\\s+(song|gana|gaana)\\s*$")
    private val NON_MUSIC_QUERY = Regex(
        "(?i)^(app|reel|reels|cricket|pubg|offline)\\b|\\b(video\\s+(games?|link|bhejo)|store\\s+se|link\\s+(bhejo|send)|download|send\\s+me)\\b"
    )
    /**
     * English function words marking a captured SEARCH_PREFIX group as
     * question text rather than a song title ("find out the meaning of this
     * song" → "out the meaning of this").
     */
    private val FUNCTION_WORDS = setOf(
        "for", "a", "the", "out", "meaning", "of", "this", "my",
        "best", "new", "old", "what", "how", "why", "which"
    )
    /** Music-service names are never song titles ("youtube song search"). */
    private val SERVICE_NAMES = Regex("(?i)\\b(youtube|spotify|ytmusic|wynk|saavn|jiosaavn)\\b")

    data class SongResult(
        val title: String,
        val artist: String,
        val artworkUrl: String,
        val audioUrl: String,
        /** PagalWorld song page — browser fallback when no stream URL exists. */
        val pageUrl: String = ""
    ) {
        fun hasStream(): Boolean = audioUrl.isNotBlank()
    }

    /**
     * Detects a song-search intent. Returns the extracted song query, or null.
     *
     * Only EXPLICIT requests match — a bare "X song" no longer hijacks normal
     * chat about songs. Supported:
     * "play kesariya", "kesariya play karo", "kesariya song sunao",
     * "song: tum hi ho", "kesariya sunao",
     * "search hum dil de chuke sanam song", "search kesariya song karo",
     * "kesariya song search karo", "kesariya song talash karo"
     */
    fun extractSongQuery(text: String): String? {
        val t = text.trim()
        // "play X" — explicit
        PLAY_PREFIX.find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q.length >= 2 && isLikelySongQuery(q)) return q
        }
        // "X play karo/kar" — explicit
        PLAY_SUFFIX.find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q.length >= 2 && isLikelySongQuery(q)) return q
        }
        // "X song/gana sunao|play|chalao|lagao" — action verb REQUIRED
        SONG_ACTION.find(t)?.let {
                val q = it.groupValues[1].trim()
                if (q.length >= 2 && isLikelySongQuery(q)) return q
            }
        // "song: X" / "gana: X" — explicit prefix
        SONG_PREFIX.find(t)?.let {
            val q = it.groupValues[2].trim()
            if (q.length >= 2 && isLikelySongQuery(q)) return q
        }
        // "search X song [karo]" — explicit search request. The captured group
        // must be real title text: questions that merely end in "song" (e.g.
        // "find out the meaning of this song") are rejected by isRealTitleText,
        // and music-service names are never song titles (same guard as the
        // suffix branch below).
        SEARCH_PREFIX.find(t)?.let {
            val q = it.groupValues[2].trim()
            if (q.length >= 2 && isLikelySongQuery(q) && isRealTitleText(q) && !isServiceName(q)) return q
        }
        // "X song search|find|…|talash [karo]" — explicit search request,
        // suffix form. Music-service names are never song titles.
        SEARCH_SUFFIX.find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q.length >= 2 && isLikelySongQuery(q) && !isServiceName(q)) return q
        }
        // "X song/gana/gaana" — bare title + song suffix. Guarded by
        // looksLikeSongTitle so normal chat ("I love this song") is not
        // hijacked — only real title-like phrases open a song card.
        SONG_SUFFIX_BARE.find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q.length >= 2 && isLikelySongQuery(q) && looksLikeSongTitle(q) && !isServiceName(q)) return q
        }
        // "X sunao" — single-word title only (avoids hijacking sentences)
        SUNAO.find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q.length >= 2 && isLikelySongQuery(q)) return q
        }
        return null
    }

    private fun isLikelySongQuery(query: String): Boolean {
        val normalized = query.trim().lowercase(java.util.Locale.ROOT)
        if (normalized == "game of thrones") return true
        if (Regex("\\bgame\\b").containsMatchIn(normalized)) return false
        return !NON_MUSIC_QUERY.containsMatchIn(normalized)
    }

    /**
     * Guards the bare "X song/gana" suffix pattern: only true when the
     * captured text looks like a song title (short noun phrase), not a
     * chat sentence. Rejects question words, sentence pronouns at the
     * start, and overly long phrases.
     */
    private fun looksLikeSongTitle(text: String): Boolean {
        val trimmed = text.trim()
        val words = trimmed.split(Regex("\\s+"))
        // Song titles are short
        if (words.size > 6 || trimmed.length > 40) return false
        val lower = words.map { it.lowercase(java.util.Locale.ROOT) }
        // Questions are not titles
        val questionWords = setOf(
            "what", "where", "when", "why", "how", "who", "which", "whom", "whose",
            "kya", "kab", "kahan", "kaise", "kyun", "kyon", "kaun", "kis", "kitna", "kitne"
        )
        if (lower.any { it in questionWords }) return false
        // Sentences starting with pronouns are chat, not titles
        val sentenceStarters = setOf(
            "i", "you", "he", "she", "we", "they", "it",
            "mai", "main", "tum", "aap", "vo", "wo", "hum", "yeh", "ye"
        )
        if (lower.firstOrNull() in sentenceStarters) return false
        return trimmed.length >= 2
    }

    /**
     * Words that mark a captured SEARCH_PREFIX group as a question rather
     * than a song title. A denylist of function words alone can never be
     * complete ("who wrote this", "how many songs"), so any interrogative /
     * question-verb token rejects the whole group and the text falls through
     * to the normal chat provider instead of opening a song card.
     */
    private val QUESTION_WORDS = setOf(
        "who", "whom", "whose", "what", "which", "when", "where", "why", "how",
        "tell", "me", "wrote", "written", "write", "sing", "sang", "sung",
        "many", "much", "is", "are", "was", "were", "do", "does", "did",
        "can", "could", "will", "would", "mean", "lyrics"
    )

    /**
     * Guards SEARCH_PREFIX against hijacking ordinary questions that merely
     * end in "song". Rejects the group when ANY token is a question word
     * ("search who wrote this song", "search how many songs did Arijit
     * sing"), and otherwise still requires at least one non-function word,
     * so single-word real titles ("search kesariya song" → "kesariya") match
     * while "find out the meaning of this song" falls through to chat.
     */
    private fun isRealTitleText(query: String): Boolean {
        val tokens = query.trim().lowercase(java.util.Locale.ROOT)
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        if (tokens.any { it in QUESTION_WORDS }) return false
        return tokens.any { it !in FUNCTION_WORDS }
    }

    /** True when [query] names a music service rather than a song. */
    private fun isServiceName(query: String): Boolean = SERVICE_NAMES.containsMatchIn(query)

    /**
     * Heuristic for a *bare* song title ("sanam Re Sanam") typed with no
     * explicit song keywords. Conservative on purpose: short text, no
     * question marks, no question words, not a sentence. The PagalWorld
     * lookup itself is the final guard — callers must only show a song card
     * when a streamable match is actually found, otherwise fall through to
     * normal chat. This keeps ordinary chat ("hello", "how are you") safe
     * while letting "sanam Re Sanam" open a playable card.
     */
    fun looksLikeBareSongTitle(text: String): Boolean {
        val t = text.trim()
        if (t.length < 3 || t.length > 60) return false
        if (t.contains('?') || t.contains('!')) return false
        val tokens = t.lowercase(java.util.Locale.ROOT)
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty() || tokens.size > 6) return false
        // Questions and chat phrases are never bare titles.
        if (tokens.any { it in QUESTION_WORDS }) return false
        if (CHAT_STARTERS.any { starter -> t.startsWith(starter, ignoreCase = true) }) return false
        return true
    }

    /** Common chat openers that must never be treated as song titles. */
    private val CHAT_STARTERS = listOf(
        "hello", "hi ", "hey ", "salam", "assalam", "aoa ",
        "how are", "what is", "what's", "tell me", "please",
        "can you", "could you", "would you", "i want", "i need",
        "mera ", "meri ", "mujhe ", "ap ", "tum ", "yeh ", "ye "
    )

    /** Label shown on song-result messages (provider-agnostic, so a plain name). */
    const val SONG_MODEL_LABEL = "Song Search"

    /**
     * Packs song fields into the `|||`-delimited attachmentName consumed by
     * SuperChatAdapter's song card. Strips `|` from ALL four fields so a
     * malicious delimiter in a remote-controlled value (og:image scraped
     * from the song page, url from the WP search API) can't shift the
     * unpacked slots when the card splits them.
     */
    fun packSongAttachment(song: SongResult): String =
        listOf(
            song.title.replace("|", ""),
            song.artist.replace("|", ""),
            song.artworkUrl.replace("|", ""),
            song.pageUrl.replace("|", "")
        ).joinToString("|||")

    /**
     * Searches PagalWorld and returns the best streamable match, or null.
     * Network I/O is bounded by the HTTP connect/read timeouts ([TIMEOUT]);
     * callers needing a tighter budget should enforce it around this call
     * with a dispatcher that actually suspends (this body is blocking IO —
     * coroutine timeouts cannot preempt it, per Kilo review).
     */
    suspend fun searchSong(query: String): SongResult? {
        // Test hook: bypass network entirely in unit tests.
        testSearchOverride?.let { return it(query) }
        return withContext(Dispatchers.IO) {
            try {
                val enc = URLEncoder.encode(query.trim(), "UTF-8")
                val searchUrl = "$BASE/wp-json/wp/v2/search?search=$enc&per_page=10"
                val body = httpGet(searchUrl) ?: return@withContext null
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val pageUrl = item.optString("url", "")
                    if (!pageUrl.contains("/song/")) continue
                    val title = decodeHtml(item.optString("title", "Unknown"))
                    val song = parseSongPage(pageUrl, title)
                    if (song != null && song.hasStream()) return@withContext song
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "search error: ${e.message}")
                null
            }
        }
    }

    private fun parseSongPage(pageUrl: String, fallbackTitle: String): SongResult? {
        val page = httpGet(pageUrl) ?: return null

        // Audio file from <audio data-file="..." data-year="..." data-month="...">
        var pickedFile: String? = null
        var pickedYear = ""
        var pickedMonth = ""
        for (m in Regex("<audio[^>]*>").findAll(page)) {
            val tag = m.value
            val f = Regex("data-file=\"([^\"]+)\"").find(tag)?.groupValues?.get(1) ?: continue
            val y = Regex("data-year=\"([^\"]*)\"").find(tag)?.groupValues?.get(1) ?: ""
            val mo = Regex("data-month=\"([^\"]*)\"").find(tag)?.groupValues?.get(1) ?: ""
            if (pickedFile == null || f.contains("320", ignoreCase = true)) {
                pickedFile = f; pickedYear = y; pickedMonth = mo
            }
        }
        var audioUrl = ""
        if (pickedFile != null && pickedYear.isNotBlank() && pickedMonth.isNotBlank()) {
            val ef = URLEncoder.encode(pickedFile, "UTF-8").replace("+", "%20")
            audioUrl = "$BASE/wp-content/uploads/$pickedYear/$pickedMonth/$ef"
        }

        val artwork = Regex("<meta property=\"og:image\" content=\"([^\"]+)\"")
            .find(page)?.groupValues?.get(1) ?: ""
        val artist = Regex("sung by ([^.<]+)").find(page)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.isNotBlank() } ?: "PagalWorld"

        return SongResult(
            title = fallbackTitle.ifBlank { "Unknown Song" },
            artist = artist,
            artworkUrl = artwork,
            audioUrl = audioUrl,
            pageUrl = pageUrl
        )
    }

    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) return null
            // Bounded read: cap at 512KB to avoid OOM on huge pages
            val sb = StringBuilder()
            conn.inputStream.bufferedReader().use { reader ->
                val buf = CharArray(8192)
                var total = 0
                while (total < MAX_RESPONSE_CHARS) {
                    val n = reader.read(buf, 0, minOf(buf.size, MAX_RESPONSE_CHARS - total))
                    if (n < 0) break
                    sb.append(buf, 0, n)
                    total += n
                }
            }
            sb.toString().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun decodeHtml(s: String): String =
        s.replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
}
