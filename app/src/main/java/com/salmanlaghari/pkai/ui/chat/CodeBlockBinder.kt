package com.salmanlaghari.pkai.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.salmanlaghari.pkai.data.repository.CodeExecutionResult
import com.salmanlaghari.pkai.databinding.ItemCodeBlockBinding
import com.salmanlaghari.pkai.util.CodeBlockParser
import com.salmanlaghari.pkai.util.MessageSegment

/**
 * Shared ```code``` block renderer for AI chat messages.
 *
 * Splits a message into text + code segments, inflates a runnable code card
 * per code segment into [container] (cleared first) and returns the visible
 * plain-text remainder for the message bubble. Used by both the Super Chat
 * adapter and the (now retired) home adapter's successor so code rendering
 * stays identical everywhere.
 *
 * @param onRunCode executes a snippet via the code runner; when null the Run
 * button is hidden and only Copy is offered.
 */
object CodeBlockBinder {

    fun bind(
        context: Context,
        container: ViewGroup,
        content: String,
        onRunCode: ((source: String, lang: String, onResult: (CodeExecutionResult) -> Unit) -> Unit)?
    ): String {
        container.removeAllViews()
        val segments = CodeBlockParser.parseSegments(content)
        val textBuilder = StringBuilder()
        var hasCodeBlocks = false

        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.Text -> {
                    if (textBuilder.isNotEmpty()) textBuilder.append("\n\n")
                    textBuilder.append(segment.content)
                }
                is MessageSegment.CodeBlock -> {
                    hasCodeBlocks = true
                    val blockBinding = ItemCodeBlockBinding.inflate(
                        LayoutInflater.from(context),
                        container,
                        true
                    )

                    val rawLangUpper = segment.rawLanguage.uppercase()
                    blockBinding.tvCodeLang.text = rawLangUpper
                    blockBinding.tvCodeContent.text = segment.code

                    // Recycling guard: tag the card with this block's identity so a
                    // late run-code result can't mutate a card recycled for other code.
                    val codeToken = segment.code.hashCode()
                    blockBinding.root.tag = codeToken

                    // Copy code button
                    blockBinding.btnCopyCode.setOnClickListener {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Code", segment.code)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    }

                    // Run code button
                    val heLang = segment.hackerEarthLang
                    if (heLang != null && onRunCode != null) {
                        blockBinding.btnRunCode.visibility = View.VISIBLE
                        blockBinding.btnRunCode.text = "▶ Run Code"
                        blockBinding.btnRunCode.isEnabled = true

                        blockBinding.btnRunCode.setOnClickListener {
                            blockBinding.btnRunCode.isEnabled = false
                            blockBinding.layoutRunLoading.visibility = View.VISIBLE
                            blockBinding.layoutRunOutputPanel.visibility = View.GONE

                            onRunCode.invoke(segment.code, heLang) { result ->
                                // Drop results for a card that was recycled/rebound
                                // since the run started (stale output would land on
                                // the wrong card, leaving this one stuck loading).
                                if (blockBinding.root.tag != codeToken) return@invoke
                                blockBinding.btnRunCode.isEnabled = true
                                blockBinding.layoutRunLoading.visibility = View.GONE
                                blockBinding.layoutRunOutputPanel.visibility = View.VISIBLE

                                when (result) {
                                    is CodeExecutionResult.Success -> {
                                        blockBinding.tvOutputStatusBadge.text = "✓ ACCEPTED (${result.runStatus})"
                                        blockBinding.tvOutputStatusBadge.setBackgroundColor(Color.parseColor("#103828"))
                                        blockBinding.tvOutputStatusBadge.setTextColor(Color.parseColor("#00E676"))
                                        blockBinding.tvOutputMetrics.text = "Time: ${String.format("%.3f", result.timeUsed)}s | Memory: ${result.memoryUsed} KB"
                                        blockBinding.tvOutputContent.text = result.stdout
                                        blockBinding.tvOutputContent.setTextColor(Color.parseColor("#00E5FF"))

                                        if (!result.stderr.isNullOrBlank()) {
                                            blockBinding.layoutErrorPanel.visibility = View.VISIBLE
                                            blockBinding.tvErrorContent.text = result.stderr
                                        } else {
                                            blockBinding.layoutErrorPanel.visibility = View.GONE
                                        }
                                        blockBinding.tvQuotaWarning.text = "Free Quota: ${result.remainingQuota}/1000 remaining"
                                    }
                                    is CodeExecutionResult.CompileError -> {
                                        blockBinding.tvOutputStatusBadge.text = "✗ COMPILATION ERROR"
                                        blockBinding.tvOutputStatusBadge.setBackgroundColor(Color.parseColor("#3D1016"))
                                        blockBinding.tvOutputStatusBadge.setTextColor(Color.parseColor("#FF5252"))
                                        blockBinding.tvOutputMetrics.text = "Failed"
                                        blockBinding.tvOutputContent.text = "(Compilation failed - see details below)"
                                        blockBinding.tvOutputContent.setTextColor(Color.parseColor("#A0AEC0"))
                                        blockBinding.layoutErrorPanel.visibility = View.VISIBLE
                                        blockBinding.tvErrorContent.text = result.compileErrorDetails
                                        blockBinding.tvQuotaWarning.text = "Quota unchanged on compile error"
                                    }
                                    is CodeExecutionResult.Error -> {
                                        blockBinding.tvOutputStatusBadge.text = "⚠️ ERROR"
                                        blockBinding.tvOutputStatusBadge.setBackgroundColor(Color.parseColor("#3D2610"))
                                        blockBinding.tvOutputStatusBadge.setTextColor(Color.parseColor("#FFAB40"))
                                        blockBinding.tvOutputMetrics.text = "Failed"
                                        blockBinding.tvOutputContent.text = result.message
                                        blockBinding.tvOutputContent.setTextColor(Color.parseColor("#FFAB40"))
                                        blockBinding.layoutErrorPanel.visibility = View.GONE
                                        blockBinding.tvQuotaWarning.text = "Check settings or network"
                                    }
                                    is CodeExecutionResult.QuotaExceeded -> {
                                        blockBinding.tvOutputStatusBadge.text = "⚠️ QUOTA EXCEEDED"
                                        blockBinding.tvOutputStatusBadge.setBackgroundColor(Color.parseColor("#3D1016"))
                                        blockBinding.tvOutputStatusBadge.setTextColor(Color.parseColor("#FF5252"))
                                        blockBinding.tvOutputMetrics.text = "0/1000 remaining"
                                        blockBinding.tvOutputContent.text = "You have reached the free quota limit of 1000 requests."
                                        blockBinding.tvOutputContent.setTextColor(Color.parseColor("#FF5252"))
                                        blockBinding.layoutErrorPanel.visibility = View.GONE
                                        blockBinding.tvQuotaWarning.text = "Quota resets daily"
                                    }
                                    is CodeExecutionResult.NoNetwork -> {
                                        blockBinding.tvOutputStatusBadge.text = "📡 NO INTERNET"
                                        blockBinding.tvOutputStatusBadge.setBackgroundColor(Color.parseColor("#383210"))
                                        blockBinding.tvOutputStatusBadge.setTextColor(Color.parseColor("#FFD700"))
                                        blockBinding.tvOutputMetrics.text = "Offline"
                                        blockBinding.tvOutputContent.text = "Please check your internet connection and try again."
                                        blockBinding.tvOutputContent.setTextColor(Color.parseColor("#FFD700"))
                                        blockBinding.layoutErrorPanel.visibility = View.GONE
                                        blockBinding.tvQuotaWarning.text = "Network required for execution"
                                    }
                                }
                            }
                        }
                    } else {
                        blockBinding.btnRunCode.visibility = View.GONE
                    }
                }
            }
        }

        container.visibility = if (hasCodeBlocks) View.VISIBLE else View.GONE
        return textBuilder.toString()
    }
}
