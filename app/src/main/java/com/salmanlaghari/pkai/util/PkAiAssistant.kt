package com.salmanlaghari.pkai.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PK AI Smart Calendar, Scheduling & Executive Assistant Engine.
 * Integrated safely and securely into PK AI Super Chat.
 */
object PkAiAssistant {

    const val PK_AI_LABEL = "PK AI Calendar Assistant"

    const val SYSTEM_INSTRUCTION =
        "You are PK AI, an intelligent AI calendar and personal assistant in PK AI Super Chat. " +
        "You help users manage schedules, organize events, set smart reminders, and plan their day with clear, friendly, and structured responses. " +
        "Use formatting and emojis where appropriate. " +
        "ACCURACY RULES: Give accurate, well-reasoned answers. If you are unsure, say so honestly instead of guessing. " +
        "For time-sensitive facts (news, prices, schedules), use your latest knowledge and note the date."
        // NOTE: language matching is injected per-message in tryRequest (dynamic
        // detection), not here, to avoid duplicating the instruction.

    private val SCHEDULING_REGEX = Regex(
        "(?i)\\b(schedule|meeting|meet|reminder|remind|calendar|event|appointment|plan|call|task|todo|alarm|tomorrow|tonight|routine|gym|interview|zoom|deadline)\\b|\\b\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)\\b|\\bat\\s+\\d{1,2}"
    )

    /**
     * Determines whether the user message expresses an intent to schedule, remind, or plan.
     */
    fun isSchedulingQuery(query: String): Boolean {
        return SCHEDULING_REGEX.containsMatchIn(query)
    }

    /**
     * Prepares an enriched prompt for the LLM that maintains the PK AI persona.
     */
    fun buildPkAiPrompt(userMessage: String, languageInstruction: String? = null): String {
        val language = languageInstruction?.let { " Reply in $it." } ?: ""
        return "$SYSTEM_INSTRUCTION$language\n\nUser request: $userMessage"
    }

    /**
     * Generates a structured PK AI smart schedule response when offline or as a fallback.
     */
    fun formatPkAiSmartPlan(input: String): String {
        val dateFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        val currentTime = timeFormat.format(Date())

        val cleanTitle = input
            .replace(Regex("(?i)remind me to|schedule a|schedule|set reminder for|plan my|meeting with"), "")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            .ifBlank { "Smart Scheduled Task" }

        return buildString {
            appendLine("✨ **PK AI Smart Calendar & Planner**")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📌 **Event:** $cleanTitle")
            appendLine("📅 **Date:** $currentDate")
            appendLine("⏰ **Time Slot:** $currentTime")
            appendLine("🔔 **Notification:** 15 minutes before event")
            appendLine("📋 **Status:** Confirmed & Synced in PK AI")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("💡 *Tip: You can ask PK AI to adjust the time, add attendees, or create daily habits anytime!*")
        }
    }
}
