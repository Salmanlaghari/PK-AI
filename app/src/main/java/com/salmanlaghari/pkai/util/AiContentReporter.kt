package com.salmanlaghari.pkai.util

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Offers a user-controlled email draft and a local-only note. There is no
 * in-app report backend: the email app opens outside PK-AI, and nothing is sent
 * until the user reviews the draft and taps Send.
 */
object AiContentReporter {
    const val SUPPORT_EMAIL = "lovefaillaghari@gmail.com"

    val REASONS: List<String> = listOf(
        "Sexual content",
        "Violent or harmful",
        "Misleading/false",
        "Other"
    )

    /** Builds the JSON payload stored for one local report. */
    fun buildReportJson(
        message: ChatMessage,
        reason: String,
        detail: String,
        timestampMs: Long = System.currentTimeMillis()
    ): String = JSONObject()
        .put("ts", timestampMs)
        .put("reason", reason)
        .put("detail", detail)
        .put("preview", message.content.take(300))
        .put("isUser", message.isUser)
        .put("model", message.modelUsed)
        .toString()

    /** Builds the body of an email draft; it is not transmitted by the app. */
    fun buildEmailReportBody(
        message: ChatMessage,
        reason: String,
        detail: String,
        timestampMs: Long = System.currentTimeMillis()
    ): String = buildString {
        appendLine("PK-AI AI-generated content report")
        appendLine("Reason: $reason")
        if (detail.isNotBlank()) appendLine("User details: $detail")
        appendLine("Model: ${message.modelUsed ?: "Unknown"}")
        appendLine("Reported at (UTC epoch milliseconds): $timestampMs")
        appendLine("Output preview (first 300 characters):")
        append(message.content.take(300))
    }

    /** Shows the reason picker and explains the external email handoff. */
    fun showReportDialog(
        context: Context,
        preferencesManager: PreferencesManager,
        message: ChatMessage,
        scope: CoroutineScope
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        val radioGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
        }
        REASONS.forEachIndexed { index, reason ->
            radioGroup.addView(
                RadioButton(context).apply {
                    text = reason
                    id = ViewGroup.generateViewId()
                    textSize = 15f
                    setPadding(0, dp(6), 0, dp(6))
                    if (index == 0) isChecked = true
                }
            )
        }
        container.addView(radioGroup)
        val detailInput = EditText(context).apply {
            hint = "Optional details…"
            setSingleLine(false)
            minLines = 2
            setPadding(dp(4), dp(12), dp(4), dp(4))
        }
        container.addView(detailInput)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.superchat_report_title))
            .setMessage(context.getString(R.string.superchat_report_email_disclosure, SUPPORT_EMAIL))
            .setView(container)
            .setPositiveButton(context.getString(R.string.superchat_report_email_button)) { _, _ ->
                val selected = radioGroup.findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
                val reason = selected?.text?.toString() ?: REASONS.first()
                openEmailDraft(context, message, reason, detailInput.text.toString().trim())
            }
            .setNegativeButton(context.getString(R.string.superchat_report_save_local)) { _, _ ->
                val selected = radioGroup.findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
                val reason = selected?.text?.toString() ?: REASONS.first()
                saveLocalNote(
                    context,
                    preferencesManager,
                    message,
                    reason,
                    detailInput.text.toString().trim(),
                    scope
                )
            }
            .setNeutralButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun openEmailDraft(context: Context, message: ChatMessage, reason: String, detail: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.fromParts("mailto", SUPPORT_EMAIL, null)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "PK-AI AI content report: $reason")
            putExtra(Intent.EXTRA_TEXT, buildEmailReportBody(message, reason, detail))
        }
        try {
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.superchat_report_email_chooser))
            )
            Toast.makeText(context, R.string.superchat_report_email_draft_opened, Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.superchat_report_email_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun saveLocalNote(
        context: Context,
        preferencesManager: PreferencesManager,
        message: ChatMessage,
        reason: String,
        detail: String,
        scope: CoroutineScope
    ) {
        val reportJson = buildReportJson(message, reason, detail)
        scope.launch {
            val saved = runCatching {
                preferencesManager.recordAiContentReport(reportJson)
            }.isSuccess
            val messageRes = if (saved) {
                R.string.superchat_report_thanks
            } else {
                R.string.superchat_report_save_failed
            }
            Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_LONG).show()
        }
    }
}
