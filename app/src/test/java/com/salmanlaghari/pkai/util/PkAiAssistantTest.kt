package com.salmanlaghari.pkai.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PkAiAssistantTest {

    @Test
    fun `isSchedulingQuery detects scheduling keywords`() {
        assertTrue(PkAiAssistant.isSchedulingQuery("Schedule a meeting tomorrow with Ali"))
        assertTrue(PkAiAssistant.isSchedulingQuery("Remind me to drink water at 4 pm"))
        assertTrue(PkAiAssistant.isSchedulingQuery("Plan my gym routine for tonight"))
        assertTrue(PkAiAssistant.isSchedulingQuery("Set an appointment with doctor"))
        assertFalse(PkAiAssistant.isSchedulingQuery("What is the capital of France?"))
    }

    @Test
    fun `buildPkAiPrompt enriches user request with persona`() {
        val prompt = "Create my agenda"
        val enriched = PkAiAssistant.buildPkAiPrompt(prompt)
        assertTrue(enriched.contains("PK AI"))
        assertTrue(enriched.contains("Create my agenda"))
    }

    @Test
    fun `formatPkAiSmartPlan formats structured calendar summary`() {
        val plan = PkAiAssistant.formatPkAiSmartPlan("meeting with Salman")
        assertTrue(plan.contains("PK AI Smart Calendar"))
        assertTrue(plan.contains("Salman"))
        assertTrue(plan.contains("Confirmed & Synced in PK AI"))
    }
}
