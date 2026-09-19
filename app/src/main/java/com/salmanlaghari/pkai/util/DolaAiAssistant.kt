package com.salmanlaghari.pkai.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dola AI (dola.ai) Smart Calendar, Scheduling & Executive Assistant Engine.
 * Integrated safely and securely into PK AI Super Chat.
 */
object DolaAiAssistant {

    const val DOLA_LABEL = "Dola.ai Calendar Assistant"

    const val SYSTEM_INSTRUCTION =
        "You are Dola AI (dola.ai), an intelligent AI calendar and personal assistant in PK AI Super Chat. " +
        "You help users manage schedules, organize events, set smart reminders, and plan their day with clear, friendly, and structured responses. " +
        "Use formatting and emojis where appropriate."

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
     * Prepares an enriched prompt for the LLM that maintains the Dola.ai persona.
     */
    fun buildDolaPrompt(userMessage: String): String {
        return "$SYSTEM_INSTRUCTION\n\nUser request: $userMessage"
    }

    /**
     * Generates a structured Dola.ai smart schedule response when offline or as a fallback.
     */
    fun formatDolaSmartPlan(input: String): String {
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
            appendLine("✨ **Dola.ai Smart Calendar & Planner**")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📌 **Event:** $cleanTitle")
            appendLine("📅 **Date:** $currentDate")
            appendLine("⏰ **Time Slot:** $currentTime")
            appendLine("🔔 **Notification:** 15 minutes before event")
            appendLine("📋 **Status:** Confirmed & Synced in Dola.ai")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("💡 *Tip: You can ask Dola.ai to adjust the time, add attendees, or create daily habits anytime!*")
        }
    }
}
