package com.salmanlaghari.pkai.ui.superchat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.util.ImageLoadHelper
import com.salmanlaghari.pkai.util.TtsHelper

/**
 * Shared per-message actions for every chat surface (Super Chat + Home).
 *
 * Extracted from SuperChatFragment so both screens offer the identical
 * speak / copy / favorite / share / fullscreen-image experience without
 * duplicating logic.
 *
 * @param context an Activity/Fragment context — the fullscreen image viewer
 * needs a window token, so do NOT pass an application context here.
 */
class ChatMessageActions(private val context: Context) {

    /** Message contents the user hearted (💖 badge); session-scoped. */
    val favoriteContents = mutableSetOf<String>()

    /** Speaks the message aloud (free TTS, language auto-detected). */
    fun speak(message: ChatMessage) {
        val lang = TtsHelper.detectLanguage(message.content)
        TtsHelper.speak(
            context = context,
            text = message.content,
            lang = lang,
            onError = { err ->
                Toast.makeText(context, "TTS error: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    /** Copies the message text to the clipboard. */
    fun copy(message: ChatMessage) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PK AI", message.content))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    /**
     * Toggles the 💖 badge on a message.
     * @return true when the message is now favorited, false when un-favorited.
     */
    fun toggleFavorite(message: ChatMessage): Boolean {
        return if (favoriteContents.add(message.content)) {
            true
        } else {
            favoriteContents.remove(message.content)
            false
        }
    }

    /** Shares the message text via the system chooser. */
    fun share(message: ChatMessage) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message.content)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }

    /**
     * Fullscreen image viewer with Share / Close actions.
     * Accepts remote URLs, content URIs and file paths — same sources the
     * chat bubbles render.
     */
    fun showFullscreenImage(source: String) {
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(imageView)
            .setPositiveButton("Close") { d, _ -> d.dismiss() }
            .setNegativeButton("Share") { _, _ ->
                runCatching {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        if (source.startsWith("http://") || source.startsWith("https://")) {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, source)
                        } else {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(source))
                        }
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share image"))
                }.onFailure {
                    Toast.makeText(context, "Could not share image", Toast.LENGTH_SHORT).show()
                }
            }
            .create()

        ImageLoadHelper.load(context, source, imageView) {
            Toast.makeText(context, "Couldn't load image for full-screen view.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
