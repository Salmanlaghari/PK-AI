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
    // "search X song" — explicit search request (Prince's feedback: plain
    // "Search Hum Dil de chuke Sanam song" must open a song card, not text).
    private val SEARCH_PREFIX = Regex("(?i)^(search|find|dhundo|dhoondo|talash)\\s+(.+?)\\s+(song|gana|gaana)\\s*$")
    // "X song search karo" — explicit search request, suffix form.
    private val SEARCH_SUFFIX = Regex("(?i)^(.+?)\\s+(song|gana|gaana)\\s+(search|find|dhundo|dhoondo)\\s*(karo|kar|karein)?\\s*$")
    private val NON_MUSIC_QUERY = Regex(
        "(?i)^(app|reel|reels|cricket|pubg|offline)\\b|\\b(video\\s+(games?|link|bhejo)|store\\s+se|link\\s+(bhejo|send)|download|send\\s+me)\\b"
    )

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
     * "search hum dil de chuke sanam song", "kesariya song search karo"
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
        // "search X song" — explicit search request
        SEARCH_PREFIX.find(t)?.let {
            val q = it.groupValues[2].trim()
            if (q.length >= 2 && isLikelySongQuery(q)) return q
        }
        // "X song search karo" — explicit search request, suffix form
        SEARCH_SUFFIX.find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q.length >= 2 && isLikelySongQuery(q)) return q
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
