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
import com.salmanlaghari.pkai.BuildConfig
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/** In-app report submission with transparent email-draft/local fallbacks. */
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
    ): String = buildString {
        append('{')
        append("\"ts\":").append(timestampMs).append(',')
        append("\"reason\":").append(quoteJson(reason)).append(',')
        append("\"detail\":").append(quoteJson(detail)).append(',')
        append("\"preview\":").append(quoteJson(message.content.take(300))).append(',')
        append("\"isUser\":").append(message.isUser).append(',')
        append("\"model\":").append(message.modelUsed?.let(::quoteJson) ?: "null")
        append('}')
    }

    /** Builds the bounded HTTPS payload accepted by reporting/apps-script/Code.gs. */
    fun buildEndpointReportJson(
        message: ChatMessage,
        reason: String,
        detail: String,
        reportId: String,
        installationId: String,
        timestampMs: Long = System.currentTimeMillis()
    ): String = buildString {
        append('{')
        append("\"version\":1,")
        append("\"reportId\":").append(quoteJson(reportId)).append(',')
        append("\"installationId\":").append(quoteJson(installationId)).append(',')
        append("\"reason\":").append(quoteJson(reason)).append(',')
        append("\"detail\":").append(quoteJson(detail.trim().take(1000))).append(',')
        append("\"preview\":").append(quoteJson(message.content.take(300))).append(',')
        append("\"model\":").append(quoteJson(message.modelUsed.orEmpty().take(120).ifBlank { "Unknown" })).append(',')
        append("\"clientTimestampMs\":").append(timestampMs).append(',')
        append("\"appVersion\":").append(quoteJson(BuildConfig.VERSION_NAME.take(30)))
        append('}')
    }

    /** Builds the body of an external email draft; it is not transmitted by the app. */
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

    private fun quoteJson(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    /** Shows the reason picker and only presents remote-submit copy when configured. */
    fun showReportDialog(
        context: Context,
        preferencesManager: PreferencesManager,
        message: ChatMessage,
        scope: CoroutineScope
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val remoteEnabled = AiReportEndpointClient.isHttpsEndpoint(BuildConfig.AI_REPORTS_ENDPOINT)

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
            maxLines = 5
            setPadding(dp(4), dp(12), dp(4), dp(4))
        }
        container.addView(detailInput)

        fun selectedReason(): String =
            radioGroup.findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
                ?.text?.toString() ?: REASONS.first()

        fun saveSelectedLocally() {
            saveLocalNote(
                context,
                preferencesManager,
                message,
                selectedReason(),
                detailInput.text.toString().trim(),
                scope
            )
        }

        val builder = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.superchat_report_title))
            .setView(container)

        if (remoteEnabled) {
            builder
                .setMessage(context.getString(R.string.superchat_report_inapp_disclosure, SUPPORT_EMAIL))
                .setPositiveButton(context.getString(R.string.superchat_report_inapp_button)) { _, _ ->
                    submitInAppReport(
                        context,
                        preferencesManager,
                        message,
                        selectedReason(),
                        detailInput.text.toString().trim(),
                        scope
                    )
                }
                .setNegativeButton(context.getString(R.string.superchat_report_email_button)) { _, _ ->
                    openEmailDraft(context, message, selectedReason(), detailInput.text.toString().trim())
                }
                .setNeutralButton(context.getString(R.string.superchat_report_save_local)) { _, _ ->
                    saveSelectedLocally()
                }
        } else {
            builder
                .setMessage(context.getString(R.string.superchat_report_email_disclosure, SUPPORT_EMAIL))
                .setPositiveButton(context.getString(R.string.superchat_report_email_button)) { _, _ ->
                    openEmailDraft(context, message, selectedReason(), detailInput.text.toString().trim())
                }
                .setNegativeButton(context.getString(R.string.superchat_report_save_local)) { _, _ ->
                    saveSelectedLocally()
                }
                .setNeutralButton("Cancel") { dialog, _ -> dialog.dismiss() }
        }
        builder.show()
    }

    private fun submitInAppReport(
        context: Context,
        preferencesManager: PreferencesManager,
        message: ChatMessage,
        reason: String,
        detail: String,
        scope: CoroutineScope
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val result = try {
                val installationId = preferencesManager.getOrCreateAiReportInstallationId()
                val payload = buildEndpointReportJson(
                    message = message,
                    reason = reason,
                    detail = detail,
                    reportId = UUID.randomUUID().toString(),
                    installationId = installationId
                )
                AiReportEndpointClient(BuildConfig.AI_REPORTS_ENDPOINT).submit(payload)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReportSubmissionResult.Failed("NETWORK_ERROR")
            }
            val messageRes = if (result == ReportSubmissionResult.Sent) {
                R.string.superchat_report_inapp_sent
            } else {
                R.string.superchat_report_inapp_failed
            }
            Toast.makeText(appContext, messageRes, Toast.LENGTH_LONG).show()
        }
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
        val appContext = context.applicationContext
        scope.launch {
            val saved = runCatching {
                preferencesManager.recordAiContentReport(reportJson)
            }.isSuccess
            val messageRes = if (saved) {
                R.string.superchat_report_thanks
            } else {
                R.string.superchat_report_save_failed
            }
            Toast.makeText(appContext, messageRes, Toast.LENGTH_LONG).show()
        }
    }
}
