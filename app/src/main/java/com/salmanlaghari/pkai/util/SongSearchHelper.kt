package com.salmanlaghari.pkai.util

import android.util.Log
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
        // "find out the meaning of this song") are rejected by isRealTitleText.
        SEARCH_PREFIX.find(t)?.let {
            val q = it.groupValues[2].trim()
            if (q.length >= 2 && isLikelySongQuery(q) && isRealTitleText(q)) return q
        }
        // "X song search|find|…|talash [karo]" — explicit search request,
        // suffix form. Music-service names are never song titles.
        SEARCH_SUFFIX.find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q.length >= 2 && isLikelySongQuery(q) && !isServiceName(q)) return q
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

    /** Searches PagalWorld and returns the best streamable match, or null. */
    suspend fun searchSong(query: String): SongResult? = withContext(Dispatchers.IO) {
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
        } catch (e: Exception) {
            Log.w(TAG, "search error: ${e.message}")
            null
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
