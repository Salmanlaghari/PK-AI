package com.salmanlaghari.pkai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tiny self-contained image loader used by the chat bubbles.
 *
 * Supports three kinds of sources so the chat can render anything the AI (or the user)
 * produces:
 *  - `data:image/<fmt>;base64,<payload>` — decoded locally, no network.
 *  - `http(s)://…` — fetched over the network.
 *  - `content://…` — opened through the [Context] content resolver (device media).
 *
 * A placeholder is shown while loading and, if decoding fails for any reason, [onError]
 * is invoked instead of crashing or dumping raw data into the bubble.
 */
object ImageLoadHelper {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun load(
        context: Context,
        source: String,
        imageView: ImageView,
        onError: (() -> Unit)? = null
    ) {
        // Cancel any in-flight load bound to this view.
        (imageView.getTag(LOAD_TAG) as? Job)?.cancel()
        imageView.setTag(LOAD_TAG, null)

        val job = scope.launch {
            val bitmap = runCatching { decode(context, source) }.getOrNull()
            withContext(Dispatchers.Main) {
                imageView.setTag(LOAD_TAG, null)
                if (bitmap == null) {
                    onError?.invoke()
                    return@withContext
                }
                if (imageView.isAttachedToWindow) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = android.view.View.VISIBLE
                }
            }
        }
        imageView.setTag(LOAD_TAG, job)
    }

    private fun decode(context: Context, source: String): Bitmap? {
        return when {
            source.startsWith("data:") -> decodeDataUri(source)
            source.startsWith("content://") -> decodeContent(context, source)
            source.startsWith("http://") || source.startsWith("https://") -> decodeRemote(source)
            else -> null
        }
    }

    private fun decodeDataUri(source: String): Bitmap? {
        val comma = source.indexOf(',')
        if (comma < 0) return null
        val meta = source.substring(0, comma)
        // Only image payloads are supported.
        if (!meta.contains("image")) return null
        val payload = source.substring(comma + 1)
        val bytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun decodeContent(context: Context, source: String): Bitmap? {
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(source))?.use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
    }

    private fun decodeRemote(source: String): Bitmap? {
        val url = URL(source)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
            setRequestProperty("Accept", "image/*")
        }
        return connection.inputStream.use { BitmapFactory.decodeStream(it) }
    }

    private const val LOAD_TAG = 0x0101_0001
}
