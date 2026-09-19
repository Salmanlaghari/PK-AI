package com.salmanlaghari.pkai.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DolaAiAssistantTest {

    @Test
    fun `isSchedulingQuery detects scheduling keywords`() {
        assertTrue(DolaAiAssistant.isSchedulingQuery("Schedule a meeting tomorrow with Ali"))
        assertTrue(DolaAiAssistant.isSchedulingQuery("Remind me to drink water at 4 pm"))
        assertTrue(DolaAiAssistant.isSchedulingQuery("Plan my gym routine for tonight"))
        assertTrue(DolaAiAssistant.isSchedulingQuery("Set an appointment with doctor"))
        assertFalse(DolaAiAssistant.isSchedulingQuery("What is the capital of France?"))
    }

    @Test
    fun `buildDolaPrompt enriches user request with persona`() {
        val prompt = "Create my agenda"
        val enriched = DolaAiAssistant.buildDolaPrompt(prompt)
        assertTrue(enriched.contains("Dola AI"))
        assertTrue(enriched.contains("Create my agenda"))
    }

    @Test
    fun `formatDolaSmartPlan formats structured calendar summary`() {
        val plan = DolaAiAssistant.formatDolaSmartPlan("meeting with Salman")
        assertTrue(plan.contains("Dola.ai Smart Calendar"))
        assertTrue(plan.contains("Salman"))
        assertTrue(plan.contains("Confirmed & Synced in Dola.ai"))
    }
}
