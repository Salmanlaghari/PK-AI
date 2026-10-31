package com.salmanlaghari.pkai.util

import com.salmanlaghari.pkai.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContentReporterTest {
    @Test
    fun `endpoint report payload contains only bounded report fields`() {
        val message = ChatMessage(
            content = "x".repeat(400),
            isUser = false,
            modelUsed = "M".repeat(150)
        )

        val payload = AiContentReporter.buildEndpointReportJson(
            message = message,
            reason = "Sexual content",
            detail = "d".repeat(1_200),
            reportId = "123e4567-e89b-42d3-a456-426614174000",
            installationId = "123e4567-e89b-42d3-a456-426614174001",
            timestampMs = 12345L
        )

        assertTrue(payload.contains("\"version\":1"))
        assertTrue(payload.contains("\"reason\":\"Sexual content\""))
        assertTrue(payload.contains("\"detail\":\"${"d".repeat(1_000)}\""))
        assertTrue(payload.contains("\"preview\":\"${"x".repeat(300)}\""))
        assertTrue(payload.contains("\"model\":\"${"M".repeat(120)}\""))
        assertTrue(payload.contains("\"clientTimestampMs\":12345"))
        assertFalse(payload.contains("\"isUser\""))
        assertFalse(payload.contains("\"email\""))
        assertFalse(payload.contains("\"conversation\""))
    }

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
