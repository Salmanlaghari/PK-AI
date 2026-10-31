package com.salmanlaghari.pkai.util

import android.app.AlertDialog
import android.content.Context
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
 * Local-only reporting notes for AI-generated content. There is currently no
 * remote endpoint: reports are stored on this device and are not received or
 * reviewed by the developer.
 */
object AiContentReporter {
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

    /** Shows the reason picker and clearly identifies that saving is local only. */
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
            .setMessage(context.getString(R.string.superchat_report_local_only))
            .setView(container)
            .setPositiveButton(context.getString(R.string.superchat_report_save_local)) { dialog, _ ->
                val selected = radioGroup.findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
                val reason = selected?.text?.toString() ?: REASONS.first()
                val reportJson = buildReportJson(message, reason, detailInput.text.toString().trim())
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
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
