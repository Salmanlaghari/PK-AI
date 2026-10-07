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
 * In-app reporting for AI-generated content (Google Play policy requirement).
 *
 * Every AI message — generated images included — shows a visible 🚩 button
 * (see `SuperChatAdapter`). Tapping it opens a reason picker; the report is
 * stored LOCALLY (there is no backend) via [PreferencesManager] as a JSON
 * entry, and the user gets a confirmation toast.
 */
object AiContentReporter {

    /** Reasons offered in the picker, in display order. */
    val REASONS: List<String> = listOf(
        "Sexual content",
        "Violent or harmful",
        "Misleading/false",
        "Other"
    )

    /**
     * Builds the JSON payload stored for one report. Kept public so the
     * storage format is unit-testable without Android UI classes.
     */
    fun buildReportJson(
        message: ChatMessage,
        reason: String,
        detail: String,
        timestampMs: Long = System.currentTimeMillis()
    ): String {
        return JSONObject()
            .put("ts", timestampMs)
            .put("reason", reason)
            .put("detail", detail)
            .put("preview", message.content.take(300))
            .put("isUser", message.isUser)
            .put("model", message.modelUsed)
            .toString()
    }

    /**
     * Shows the report reason-picker dialog for [message]. On submit the
     * report is recorded locally and a confirmation toast is shown.
     */
    fun showReportDialog(
        context: Context,
        preferencesManager: PreferencesManager,
        message: ChatMessage,
        scope: CoroutineScope
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

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
            .setMessage("Is AI-generated content ke baare mein report karein. Aapki report record kar li jayegi.")
            .setView(container)
            .setPositiveButton("Report") { dialog, _ ->
                val checkedId = radioGroup.checkedRadioButtonId
                val selected = radioGroup.findViewById<RadioButton>(checkedId)
                val reason = selected?.text?.toString() ?: REASONS.first()
                val detail = detailInput.text.toString().trim()
                val reportJson = buildReportJson(message, reason, detail)
                scope.launch {
                    runCatching { preferencesManager.recordAiContentReport(reportJson) }
                    Toast.makeText(
                        context,
                        context.getString(R.string.superchat_report_thanks),
                        Toast.LENGTH_LONG
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
