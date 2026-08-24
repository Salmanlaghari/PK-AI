package com.salmanlaghari.pkai.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

/**
 * Free Text-to-Speech helper using Google Translate TTS.
 *
 * No API key, no signup — completely free. Supports multiple languages.
 * Audio is fetched as MP3 and played via Android MediaPlayer.
 */
object TtsHelper {

    private const val TAG = "TtsHelper"
    private const val BASE_URL = "https://translate.google.com/translate_tts"
    private const val MAX_CHARS = 200 // Google TTS limit per request

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var currentText: String? = null

    /**
     * Speaks the given text using Google Translate TTS.
     * Stops any currently playing speech first.
     *
     * @param context Android context
     * @param text The text to speak
     * @param lang Language code (e.g., "en", "ur", "hi", "ar")
     * @param onComplete Called when playback finishes
     * @param onError Called on error
     */
    fun speak(
        context: Context,
        text: String,
        lang: String = "en",
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // If same text is playing, stop it
        if (isPlaying && currentText == text) {
            stop()
            onComplete()
            return
        }

        // Stop any current playback
        stop()

        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            onError("Nothing to speak")
            return
        }

        // Split long text into chunks
        val chunks = splitText(cleanText, MAX_CHARS)
        if (chunks.isEmpty()) {
            onError("Nothing to speak")
            return
        }

        currentText = text
        isPlaying = true

        // Play chunks sequentially
        playChunks(context, chunks, 0, lang, onComplete, onError)
    }

    /**
     * Downloads and plays a single TTS chunk, then moves to the next.
     */
    private fun playChunks(
        context: Context,
        chunks: List<String>,
        index: Int,
        lang: String,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (index >= chunks.size) {
            isPlaying = false
            currentText = null
            onComplete()
            return
        }

        val chunk = chunks[index]
        val encodedText = URLEncoder.encode(chunk, "UTF-8")
        val url = "$BASE_URL?ie=UTF-8&tl=$lang&client=tw-ob&q=$encodedText"

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            // Download on background thread
            Thread {
                try {
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            isPlaying = false
                            currentText = null
                            onError("TTS request failed (HTTP ${response.code})")
                        }
                        return@Thread
                    }

                    val body = response.body ?: run {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            isPlaying = false
                            currentText = null
                            onError("Empty TTS response")
                        }
                        return@Thread
                    }

                    // Save to temp file
                    val tempFile = File(context.cacheDir, "tts_chunk_${index}.mp3")
                    val fos = FileOutputStream(tempFile)
                    body.byteStream().copyTo(fos)
                    fos.close()
                    body.close()

                    // Play on main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        playAudio(context, tempFile) {
                            // After this chunk finishes, play next
                            playChunks(context, chunks, index + 1, lang, onComplete, onError)
                        }
                    }
                } catch (e: Exception) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        isPlaying = false
                        currentText = null
                        onError("Network error: ${e.localizedMessage}")
                    }
                }
            }.start()
        } catch (e: Exception) {
            isPlaying = false
            currentText = null
            onError("Error: ${e.localizedMessage}")
        }
    }

    /**
     * Plays an audio file using MediaPlayer.
     */
    private fun playAudio(context: Context, file: File, onComplete: () -> Unit) {
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                it.release()
                mediaPlayer = null
                file.delete()
                onComplete()
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                mediaPlayer = null
                file.delete()
                isPlaying = false
                currentText = null
                onComplete()
                true
            }
            player.prepare()
            player.start()
            mediaPlayer = player
        } catch (e: Exception) {
            file.delete()
            isPlaying = false
            currentText = null
            onComplete()
        }
    }

    /**
     * Stops any currently playing speech.
     */
    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isPlaying = false
        currentText = null
    }

    /**
     * Returns true if TTS is currently playing.
     */
    fun isSpeaking(): Boolean = isPlaying

    /**
     * Splits text into chunks at sentence boundaries.
     */
    private fun splitText(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLen) {
                chunks.add(remaining)
                break
            }

            // Find a good break point (sentence end, comma, space)
            var breakAt = maxLen
            val sentenceEnd = remaining.lastIndexOfAny(charArrayOf('.', '!', '?'), maxLen)
            if (sentenceEnd > maxLen / 2) {
                breakAt = sentenceEnd + 1
            } else {
                val comma = remaining.lastIndexOf(',', maxLen)
                if (comma > maxLen / 2) {
                    breakAt = comma + 1
                } else {
                    val space = remaining.lastIndexOf(' ', maxLen)
                    if (space > 0) breakAt = space + 1
                }
            }

            chunks.add(remaining.substring(0, breakAt).trim())
            remaining = remaining.substring(breakAt).trim()
        }

        return chunks.filter { it.isNotBlank() }
    }

    /**
     * Detects language from text content for better TTS.
     */
    fun detectLanguage(text: String): String {
        val t = text.trim()
        // Urdu / Arabic script
        if (t.any { it.code in 0x0600..0x06FF }) return "ur"
        // Hindi / Devanagari
        if (t.any { it.code in 0x0900..0x097F }) return "hi"
        // Chinese
        if (t.any { it.code in 0x4E00..0x9FFF }) return "zh"
        // Japanese
        if (t.any { it.code in 0x3040..0x309F }) return "ja"
        // Korean
        if (t.any { it.code in 0xAC00..0xD7AF }) return "ko"
        // Arabic
        if (t.any { it.code in 0x0627..0x064A }) return "ar"
        // Default to English
        return "en"
    }
}
