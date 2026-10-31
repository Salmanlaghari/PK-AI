package com.salmanlaghari.pkai.util

import com.salmanlaghari.pkai.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContentReporterTest {
    @Test
    fun `email draft uses the approved support address and limits output preview`() {
        val content = "x".repeat(350)
        val message = ChatMessage(
            content = content,
            isUser = false,
            modelUsed = "Test model"
        )

        val body = AiContentReporter.buildEmailReportBody(
            message = message,
            reason = "Sexual content",
            detail = "Unexpected generated result",
            timestampMs = 12345L
        )

        assertEquals("lovefaillaghari@gmail.com", AiContentReporter.SUPPORT_EMAIL)
        assertTrue(body.contains("Reason: Sexual content"))
        assertTrue(body.contains("User details: Unexpected generated result"))
        assertTrue(body.contains("Model: Test model"))
        assertTrue(body.contains("12345"))
        assertTrue(body.contains("x".repeat(300)))
        assertFalse(body.contains("x".repeat(301)))
    }
}
