package com.salmanlaghari.pkai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import com.salmanlaghari.pkai.R
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
        (imageView.getTag(R.id.tag_image_load_job) as? Job)?.cancel()
        imageView.setTag(R.id.tag_image_load_job, null)

        val job = scope.launch {
            val bitmap = runCatching { decode(context, source) }.getOrNull()
            withContext(Dispatchers.Main) {
                imageView.setTag(R.id.tag_image_load_job, null)
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
        imageView.setTag(R.id.tag_image_load_job, job)
    }

    private fun decode(context: Context, source: String): Bitmap? {
        return when {
            source.startsWith("file://") -> decodeFile(source.removePrefix("file://"))
            source.startsWith("/") -> decodeFile(source)
            source.startsWith("data:") -> decodeDataUri(source)
            source.startsWith("content://") -> decodeContent(context, source)
            source.startsWith("http://") || source.startsWith("https://") -> decodeRemote(source)
            else -> null
        }
    }

    private fun decodeFile(filePath: String): Bitmap? {
        return runCatching {
            val file = java.io.File(filePath)
            if (!file.exists() || !file.canRead()) return null

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            var sample = 1
            val maxDimension = 1200
            val maxOut = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxOut / sample > maxDimension) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    private fun decodeDataUri(source: String): Bitmap? {
        val comma = source.indexOf(',')
        if (comma < 0) return null
        val meta = source.substring(0, comma)
        // Only image payloads are supported.
        if (!meta.contains("image")) return null
        val payload = source.substring(comma + 1)
        val bytes = runCatching {
            android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        }.getOrNull() ?: return null

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        var sample = 1
        val maxDimension = 1200
        val maxOut = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
        while (maxOut / sample > maxDimension) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
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
}
